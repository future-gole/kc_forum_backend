import http from 'k6/http';
import { check } from 'k6';

export const options = {
    setupTimeout: '5m',
    teardownTimeout: '5m',
};

const BASE_URL = 'http://119.91.218.96:58080';

export function setup() {
    const users = [];
    const totalUsers = 1; // 测试模式：只生成1个用户

    // 输出到 stderr，避免污染 stdout
    console.error(`[Info] Starting to generate ${totalUsers} user(s)...`); 

    for (let i = 0; i < totalUsers; i++) {
        const timestamp = new Date().getTime();
        const uniqueId = `${timestamp}_${i}`;
        const email = `test_user_${uniqueId}@test.com`;
        const password = `Pass_${uniqueId}`;
        const userName = `User_${uniqueId}`;

        // 1. 注册
        const regPayload = JSON.stringify({
            userName: userName,
            nickName: `Nick_${uniqueId}`,
            email: email,
            password: password,
            repeatPassword: password,
            code: "123456"
        });

        const regRes = http.post(`${BASE_URL}/user/register`, regPayload, {
            headers: { 'Content-Type': 'application/json' },
        });

        if (regRes.status !== 200) {
            console.error(`[Error] Register failed for ${email}: ${regRes.body}`);
            continue;
        }

        // 2. 登录
        const loginPayload = JSON.stringify({
            email: email,
            password: password
        });

        const loginRes = http.post(`${BASE_URL}/user/login`, loginPayload, {
            headers: { 'Content-Type': 'application/json' },
        });

        if (loginRes.status === 200) {
            try {
                const body = JSON.parse(loginRes.body);
                // 获取 Token
                let token = body.data && body.data.authorization ? body.data.authorization : "";
                
                if (token) {
                    users.push({
                        email: email,
                        password: password,
                        token: token
                    });
                    console.error(`[Success] User created: ${email}`);
                }
            } catch (e) {
                console.error(`[Error] Parse login response failed: ${e}`);
            }
        } else {
            console.error(`[Error] Login failed for ${email}: ${loginRes.body}`);
        }
    }

    return users;
}

export default function () {
    // No-op
}

export function teardown(users) {
    // 输出单行 JSON，前缀 DATA_JSON=
    console.log("DATA_JSON=" + JSON.stringify(users));
}

export function handleSummary(data) {
    // 禁止 K6 输出默认摘要到 stdout
    return {};
}
