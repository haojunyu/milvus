// Copyright (C) 2019-2020 Zilliz. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied. See the License for the specific language governing permissions and limitations under the License.

#pragma once

#include <string>

#include "ConfigNode.h"
#include "utils/Status.h"

namespace milvus {
namespace server {

/* 抽象类ConfigMgr,只要有一個函数是纯虚函数，接口的角色 */
class ConfigMgr {
 public:
    virtual Status
    LoadConfigFile(const std::string& filename) = 0;

    virtual void
    Print() const = 0;  // will be deleted

    virtual std::string
    DumpString() const = 0;

    virtual const ConfigNode&
    GetRootNode() const = 0;

    virtual ConfigNode&
    GetRootNode() = 0;
};

}  // namespace server
}  // namespace milvus
