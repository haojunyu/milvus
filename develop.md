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


依赖的c++库

* GTest
* MySQLPP
* Prometheus
* SQLite
* yaml-cpp  处理yaml配置文件
* libunwind
* gperftools
* GRPC
* ZLIB
* Opentracing 分布式链路跟踪
* fiu  libfiu是一个故障注入的C库。它提供用于标识“故障点”（“核心API”）里面的代码，功能启用/禁用这些点的失败（“控制API”）。
* AWS
* oatpp
* armadillo
* json https://github.com/nlohmann/json
* logging 



