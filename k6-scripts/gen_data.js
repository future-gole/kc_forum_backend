import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    setupTimeout: '30m',
    teardownTimeout: '5m',
};

const BASE_URL = 'http://119.91.218.96:58080';

export function setup() {
    const users = [];
    const totalUsers = 1000;
    const batchSize = 20; // 适度增加并发

    console.error(`[Info] Starting to generate ${totalUsers} users...`); 

    for (let i = 0; i < totalUsers; i += batchSize) {
        const currentBatchSize = Math.min(batchSize, totalUsers - i);
        const registerReqs = {};
        const loginReqs = {};
        const batchUsers = [];

        // 1. 准备注册请求
        for (let j = 0; j < currentBatchSize; j++) {
            const userIndex = i + j;
            const timestamp = new Date().getTime();
            const uniqueId = `${timestamp}_${userIndex}`;
            // 缩短用户名和昵称，避免超过20字符限制
            // timestamp 13位 + _ + index (最多4位) = 18位，接近极限
            // 改用更短的 ID 生成策略
            const shortId = `${i}_${j}`; 
            const email = `u${shortId}_${timestamp}@test.com`;
            const password = `Pass_${shortId}`;
            const userName = `U${shortId}`; // U0_0, U1000_0
            const nickName = `N${shortId}`;

            batchUsers.push({ email, password, userName, uniqueId: shortId });

            const regPayload = JSON.stringify({
                userName: userName,
                nickName: nickName,
                email: email,
                password: password,
                repeatPassword: password,
                code: "123456"
            });

            registerReqs[userIndex] = {
                method: 'POST',
                url: `${BASE_URL}/user/register`,
                body: regPayload,
                params: { headers: { 'Content-Type': 'application/json' } }
            };
        }

        // 2. 批量注册
        const regResponses = http.batch(registerReqs);

        sleep(1); // 等待1秒，确保数据库事务提交

        // 3. 准备登录请求 (仅针对注册成功的)
        for (let j = 0; j < currentBatchSize; j++) {
            const userIndex = i + j;
            const regRes = regResponses[userIndex];
            const user = batchUsers[j];

            // 严格检查注册状态
            if (!regRes || regRes.status !== 200) {
                const status = regRes ? regRes.status : 'undefined';
                const body = regRes ? regRes.body : 'undefined';
                console.error(`[Error] Register failed for ${user.email}: ${status} ${body}`);
                continue; // 注册失败则跳过登录
            }
            
            // 简单检查注册状态，这里假设大部分成功，即使失败也尝试登录(会失败)或者跳过
            // 为了简单，直接构建登录请求
            const loginPayload = JSON.stringify({
                email: user.email,
                password: user.password
            });

            loginReqs[userIndex] = {
                method: 'POST',
                url: `${BASE_URL}/user/login`,
                body: loginPayload,
                params: { headers: { 'Content-Type': 'application/json' } }
            };
        }

        // 4. 批量登录
        const loginResponses = http.batch(loginReqs);

        // 5. 处理登录结果
        for (let j = 0; j < currentBatchSize; j++) {
            const userIndex = i + j;
            const res = loginResponses[userIndex];
            const user = batchUsers[j];

            if (res.status === 200) {
                try {
                    const body = JSON.parse(res.body);
                    let token = body.data && body.data.authorization ? body.data.authorization : "";
                    if (token) {
                        users.push({
                            email: user.email,
                            password: user.password,
                            token: token
                        });
                    }
                } catch (e) {
                    // ignore
                }
            }
        }

        console.error(`[Info] Processed ${Math.min(i + batchSize, totalUsers)}/${totalUsers} users...`);
        sleep(0.2); // 稍微休眠，给服务器喘息时间
    }

    console.error(`[Success] Generated ${users.length} valid users.`);
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
    return {};
}
