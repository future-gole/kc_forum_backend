# kc_forum
## 项目简介

- KC Forum Backend 是一个基于 Spring Boot 开发的现代化论坛后端系统，提供完整的论坛功能，包括用户管理、文章发布、评论回复、社交互动等核心功能。
- 技术栈：springboot redis mybatis-plus mysql

## 主要功能

### 核心功能模块

1. **用户管理系统**
   - 用户注册、登录、个人资料管理
   - JWT 身份认证和授权 

2. **文章管理**
   - 文章创建、编辑、删除、查看
   - 文章分类和标签管理

3. **回复系统**
   - 支持多级嵌套回复
   - 回复管理和删除功能
4. **社交互动**
   - 文章和回复点赞功能
   - 社交数据统计

5. **板块管理**
   - 论坛分类和板块组织

6. **图片管理**
   - 文章图片上传和管理

## 技术栈

### 后端框架
- **Spring Boot 3.4.2** - 主要应用框架
- **Spring Security** - 安全框架
- **MyBatis-Plus** - ORM 框架 

### 数据存储
- **MySQL** - 主要数据库
- **Redis** - 缓存和会话存储
### 认证授权
- **JWT (JSON Web Token)** - 身份认证

### 构建工具
- **Maven** - 项目构建和依赖管理 

## 项目结构

```
src/main/java/com/doublez/kc_forum/
├── common/          # 公共组件和配置
├── controller/      # REST API 控制器
├── mapper/          # 数据访问层
├── model/           # 数据模型和实体
├── service/         # 业务逻辑服务层
└── KcForumApplication.java  # 应用入口
```

## 数据模型

### 核心实体

1. **用户 (User)**
   - 用户基本信息和账户管理 

2. **文章 (Article)**
   - 文章内容和元数据管理

3. **回复 (ArticleReply)**
   - 支持嵌套回复的评论系统 

4. **板块 (Board)**
   - 论坛分类和板块结构 
5. **点赞 (Likes)**
   - 用户点赞数据跟踪
6. **消息(message)**
## 安装和配置

### 环境要求
- Java 17 或更高版本
- MySQL 8.0+
- Redis 6.0+
- Maven 3.6+ 

### 配置步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd kc_forum_backend
```

2. **配置应用参数**
   - 复制配置模板：
   ```bash
   cp src/main/resources/application-template.yml src/main/resources/application-dev.yml
   ```
   
3. **修改配置文件**
   编辑 `application-dev.yml` 配置以下参数：
   - 数据库连接信息
   - Redis 连接配置
   - JWT 密钥设置
   - 文件上传路径

4. **创建数据库**
   根据实体类中的注释创建相应的数据库表

## 安装教程
1. 正确的拉取pom文件中的相关依赖即可，注意项目中的@lombok版本有些依赖需要注释掉否则无法启动
2. 根据application-template.yml中的配置进行修改为 application-dev 或者application-prod.yml 即可
3. 启动项目
## 使用说明
与前端搭配使用：https://github.com/future-gole/kc_forum_fronted

## API 文档

### REST API 架构

系统提供完整的 RESTful API，所有控制器都使用 `@RestController` 注解，并通过统一的响应处理机制格式化输出。 [18](#0-17) 

### 主要 API 端点

1. **用户相关 API**
   - 用户注册、登录、个人信息管理

2. **文章相关 API**
   - CRUD 操作和文章查询

3. **回复相关 API**
   - 评论和回复管理

4. **点赞相关 API**
   - 点赞和取消点赞操作

5. **板块相关 API**
   - 论坛分类管理

6. **图片相关 API**
   - 图片上传和管理
7. **消息相关API**
   - 消息的处理 

### 认证机制

API 使用 JWT 进行身份认证，通过拦截器进行统一的权限验证。 [19](#0-18) 

## 系统架构

### 分层架构

1. **控制器层 (Controller)**
   - 处理 HTTP 请求和响应
   - 参数验证和格式转换

2. **服务层 (Service)**
   - 业务逻辑处理
   - 事务管理 [20](#0-19) 

3. **数据访问层 (Mapper)**
   - 数据库操作和 SQL 映射

4. **模型层 (Model)**
   - 数据实体和 DTO 对象

### 缓存策略

系统使用 Redis 进行缓存优化，包括：
- 用户会话管理
- 热点数据缓存
- 文章元数据缓存

