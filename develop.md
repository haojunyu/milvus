# 1.0 源码阅读

## 环境部署

```bash
# 容器开发环境
docker run -it --name milvus_env -v $PWD:/data -p 19530:19530 -d milvusdb/milvus-cpu-build-env:v0.7.0-ubuntu18.04
# 进入容器
docker exec -it milvus_env bash
# 检查依赖
git --version # 2.17.1
cmake --version # 3.14.3
# 编译
cd /data/core && ./build.sh -t Release
```