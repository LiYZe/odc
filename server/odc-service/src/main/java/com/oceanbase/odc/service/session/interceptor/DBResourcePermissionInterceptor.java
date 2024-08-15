/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.service.session.interceptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.constant.OrganizationType;
import com.oceanbase.odc.core.sql.execute.SqlExecuteStages;
import com.oceanbase.odc.service.connection.database.DatabaseService;
import com.oceanbase.odc.service.connection.database.model.DBResource;
import com.oceanbase.odc.service.connection.database.model.UnauthorizedDBResource;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.iam.auth.AuthenticationFacade;
import com.oceanbase.odc.service.permission.DBResourcePermissionHelper;
import com.oceanbase.odc.service.permission.database.model.DatabasePermissionType;
import com.oceanbase.odc.service.session.model.AsyncExecuteContext;
import com.oceanbase.odc.service.session.model.SqlAsyncExecuteReq;
import com.oceanbase.odc.service.session.model.SqlAsyncExecuteResp;
import com.oceanbase.odc.service.session.model.SqlExecuteResult;
import com.oceanbase.odc.service.session.model.SqlTuplesWithViolation;
import com.oceanbase.odc.service.session.util.DBSchemaExtractor;
import com.oceanbase.odc.service.session.util.DBSchemaExtractor.DBSchemaIdentity;
import com.oceanbase.tools.dbbrowser.parser.constant.SqlType;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @Author: fenghao
 * @Create 2024/3/20 14:44
 * @Version 1.0
 */
@Slf4j
@Component
public class DBResourcePermissionInterceptor extends BaseTimeConsumingInterceptor {
    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private AuthenticationFacade authenticationFacade;

    @Autowired
    private DBResourcePermissionHelper dbResourcePermissionHelper;

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    /**
     * 处理前的操作
     * 
     * @param request SqlAsyncExecuteReq对象，表示异步执行SQL请求
     * @param response SqlAsyncExecuteResp对象，表示异步执行SQL响应
     * @param session ConnectionSession对象，表示数据库连接会话
     * @param context AsyncExecuteContext对象，表示异步执行上下文
     * @return boolean值，表示是否通过认证
     * @throws Exception 异常
     */
    public boolean doPreHandle(@NonNull SqlAsyncExecuteReq request, @NonNull SqlAsyncExecuteResp response,
            @NonNull ConnectionSession session, @NonNull AsyncExecuteContext context) throws Exception {
        // 判断当前用户是否为个人版用户，如果是则直接通过认证
        if (authenticationFacade.currentUser().getOrganizationType() == OrganizationType.INDIVIDUAL) {
            return true;
        }
        // 获取数据库连接配置
        ConnectionConfig connectionConfig = (ConnectionConfig) ConnectionSessionUtil.getConnectionConfig(session);
        // 获取已存在的数据库名称集合
        Set<String> existedDatabaseNames =
                databaseService.listDatabasesByConnectionIds(Collections.singleton(connectionConfig.getId()))
                        .stream().filter(database -> database.getExisted()).map(database -> database.getName())
                        .collect(Collectors.toSet());

        // 获取当前模式
        String currentSchema = ConnectionSessionUtil.getCurrentSchema(session);
        // 获取SQL语句中的数据库模式和SQL类型的映射关系
        Map<DBSchemaIdentity, Set<SqlType>> identity2Types = DBSchemaExtractor.listDBSchemasWithSqlTypes(
                response.getSqls().stream().map(SqlTuplesWithViolation::getSqlTuple).collect(Collectors.toList()),
                session.getDialectType(), currentSchema).entrySet().stream()
                .filter(entry -> Objects.isNull(entry.getKey().getSchema())
                        || existedDatabaseNames.contains(entry.getKey().getSchema()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // 获取数据库资源和权限类型的映射关系
        Map<DBResource, Set<DatabasePermissionType>> resource2PermissionTypes =
                DBResourcePermissionHelper.getDBResource2PermissionTypes(identity2Types, connectionConfig, null);
        // 过滤未授权的数据库资源
        List<UnauthorizedDBResource> unauthorizedDBResource = dbResourcePermissionHelper
                .filterUnauthorizedDBResources(resource2PermissionTypes, false);
        // 如果存在未授权的数据库资源，则设置响应的未授权数据库资源并返回false，否则返回true
        if (CollectionUtils.isNotEmpty(unauthorizedDBResource)) {
            response.setUnauthorizedDBResources(unauthorizedDBResource);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull SqlExecuteResult response, @NonNull ConnectionSession session,
            @NonNull AsyncExecuteContext context) {}

    @Override
    protected String getExecuteStageName() {
        return SqlExecuteStages.DATABASE_PERMISSION_CHECK;
    }

}
