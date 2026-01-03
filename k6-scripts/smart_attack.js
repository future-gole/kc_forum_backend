import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// 读取本地生成的 users.json
const users = new SharedArray('users', function () {
    return JSON.parse(open('/k6/users.json'));
});

// 本地 Token 缓存 (Map<email, token>)，用于在当前 VU 生命周期内保存刷新后的 Token
const tokenCache = new Map();

export const options = {
    vus: 10, // 并发数
    duration: '30s', // 每个 ID 压测时长
};

const BASE_URL = 'http://119.91.218.96:58080';

export default function () {
    const userIndex = exec.scenario.iterationInTest % users.length;
    const user = users[userIndex];
    
    const targetId = __ENV.TARGET_ID;

    if (!targetId) {
        console.error('TARGET_ID is missing!');
        exec.test.abort();
    }

    performLike(user, targetId);
    
    sleep(0.1); 
}

function performLike(user, targetId) {
    const url = `${BASE_URL}/likes/addLike?targetId=${targetId}&targetType=article`;
    
    // 优先使用缓存中的 Token，否则使用初始 Token
    let currentToken = tokenCache.get(user.email) || user.token;

    let authHeader = currentToken;
    if (!authHeader.startsWith('Bearer ')) {
        authHeader = `Bearer ${authHeader}`;
    }

    const params = {
        headers: {
            'Authorization': authHeader,
            'Content-Type': 'application/json',
        },
    };

    let res = http.post(url, null, params);

    // 401 自动续期逻辑
    if (res.status === 401) {
        console.warn(`[401] Token expired for ${user.email}, refreshing...`);
        
        const newToken = refreshToken(user);
        if (newToken) {
            // 更新本地缓存
            tokenCache.set(user.email, newToken);

            let newAuthHeader = newToken;
            if (!newAuthHeader.startsWith('Bearer ')) {
                newAuthHeader = `Bearer ${newAuthHeader}`;
            }
            
            const retryParams = {
                headers: {
                    'Authorization': newAuthHeader,
                    'Content-Type': 'application/json',
                },
            };
            
            const retryRes = http.post(url, null, retryParams);
            
            check(retryRes, {
                'Retry Like 200': (r) => r.status === 200,
            });
        }
    } else {
        check(res, {
            'Like 200': (r) => r.status === 200,
        });
        
        // 如果失败且不是401，打印错误详情以便调试 (例如重复点赞)
        if (res.status !== 200) {
            // 避免日志过多，只打印部分
            if (Math.random() < 0.05) { 
                console.error(`[Error] Like failed: ${res.status} - ${res.body}`);
            }
        }
    }
}

function refreshToken(user) {
    const loginPayload = JSON.stringify({
        email: user.email,
        password: user.password
    });

    const res = http.post(`${BASE_URL}/user/login`, loginPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    if (res.status === 200) {
        try {
            const body = JSON.parse(res.body);
            const newToken = body.data && body.data.authorization;
            if (newToken) {
                return newToken;
            }
        } catch (e) {
            console.error(`[Refresh] Parse error: ${e}`);
        }
    }
    
    console.error(`[Refresh] Failed for ${user.email}: ${res.status}`);
    return null;
}
