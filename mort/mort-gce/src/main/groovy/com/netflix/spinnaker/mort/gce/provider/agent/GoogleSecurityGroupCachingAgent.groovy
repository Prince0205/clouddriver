/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.mort.gce.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.mort.gce.cache.Keys
import com.netflix.spinnaker.mort.gce.provider.GoogleInfrastructureProvider
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class GoogleSecurityGroupCachingAgent implements CachingAgent, OnDemandAgent {

  private static final String ON_DEMAND_TYPE = 'GoogleSecurityGroup'

  final String accountName
  final GoogleCredentials credentials
  final ObjectMapper objectMapper

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.SECURITY_GROUPS.ns)
  ] as Set)

  GoogleSecurityGroupCachingAgent(String accountName, GoogleCredentials credentials, ObjectMapper objectMapper, ExtendedRegistry extendedRegistry) {
    this.accountName = accountName
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(extendedRegistry, this, ON_DEMAND_TYPE)
  }

  @Override
  String getProviderName() {
    GoogleInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/global/${GoogleSecurityGroupCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getOnDemandAgentType() {
    getAgentType()
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.account != accountName) {
      return null
    }

    if (data.region != "global") {
      return null
    }

    new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(),
      authoritativeTypes: [Keys.Namespace.SECURITY_GROUPS.ns],
      cacheResult: buildCacheResult(providerCache)
    )
  }

  @Override
  boolean handles(String type) {
    type == "GoogleSecurityGroup"
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    buildCacheResult(providerCache)
  }

  private CacheResult buildCacheResult(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    def compute = credentials.compute
    def project = credentials.project

    List<Firewall> firewallList = compute.firewalls().list(project).execute().items

    List<CacheData> data = firewallList.collect { Firewall firewall ->
      Map<String, Object> attributes = [firewall: firewall]

      new DefaultCacheData(Keys.getSecurityGroupKey(firewall.getName(), firewall.getName(), "global", accountName, null),
                           attributes,
                           [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")

    new DefaultCacheResult([(Keys.Namespace.SECURITY_GROUPS.ns): data])
  }
}
