// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.elasticsearch;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.bulk.BulkRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.client.Response;

public class ElasticGroupIndex extends AbstractElasticIndex<AccountGroup.UUID, InternalGroup>
    implements GroupIndex {
  static class GroupMapping {
    MappingProperties groups;

    GroupMapping(Schema<InternalGroup> schema) {
      this.groups = ElasticMapping.createMapping(schema);
    }
  }

  static final String GROUPS = "groups";

  private final GroupMapping mapping;
  private final Provider<GroupCache> groupCache;
  private final Schema<InternalGroup> schema;

  @Inject
  ElasticGroupIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<GroupCache> groupCache,
      ElasticRestClientBuilder clientBuilder,
      @Assisted Schema<InternalGroup> schema) {
    super(cfg, sitePaths, schema, clientBuilder, GROUPS);
    this.groupCache = groupCache;
    this.mapping = new GroupMapping(schema);
    this.schema = schema;
  }

  @Override
  public void replace(InternalGroup group) throws IOException {
    BulkRequest bulk =
        new IndexRequest(getId(group), indexName, GROUPS).add(new UpdateRequest<>(schema, group));

    String uri = getURI(GROUPS, BULK);
    Response response = postRequest(bulk, uri, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new IOException(
          String.format(
              "Failed to replace group %s in index %s: %s",
              group.getGroupUUID().get(), indexName, statusCode));
    }
  }

  @Override
  public DataSource<InternalGroup> getSource(Predicate<InternalGroup> p, QueryOptions opts)
      throws QueryParseException {
    JsonArray sortArray = getSortArray(GroupField.UUID.getName());
    return new ElasticQuerySource(p, opts.filterFields(IndexUtils::groupFields), GROUPS, sortArray);
  }

  @Override
  protected String addActions(AccountGroup.UUID c) {
    return delete(GROUPS, c);
  }

  @Override
  protected String getMappings() {
    ImmutableMap<String, GroupMapping> mappings = ImmutableMap.of("mappings", mapping);
    return gson.toJson(mappings);
  }

  @Override
  protected String getId(InternalGroup group) {
    return group.getGroupUUID().get();
  }

  @Override
  protected InternalGroup fromDocument(JsonObject json, Set<String> fields) {
    JsonElement source = json.get("_source");
    if (source == null) {
      source = json.getAsJsonObject().get("fields");
    }

    AccountGroup.UUID uuid =
        new AccountGroup.UUID(
            source.getAsJsonObject().get(GroupField.UUID.getName()).getAsString());
    // Use the GroupCache rather than depending on any stored fields in the
    // document (of which there shouldn't be any).
    return groupCache.get().get(uuid).orElse(null);
  }
}