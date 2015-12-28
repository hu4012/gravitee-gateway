/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.services.sync;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.gravitee.gateway.core.definition.Api;
import io.gravitee.gateway.core.manager.ApiManager;
import io.gravitee.gateway.services.sync.builder.RepositoryApiBuilder;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiRepository;
import io.gravitee.repository.management.api.EventRepository;
import io.gravitee.repository.management.model.Event;
import io.gravitee.repository.management.model.EventType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.SimpleType;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class SyncManagerTest {

    @InjectMocks
    private SyncManager syncManager = new SyncManager();

    @Mock
    private ApiRepository apiRepository;
    
    @Mock
    private EventRepository eventRepository;

    @Mock
    private ApiManager apiManager;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    public void test_empty() throws TechnicalException {
        when(apiRepository.findAll()).thenReturn(Collections.emptySet());

        syncManager.refresh();

        verify(apiManager, never()).deploy(any(Api.class));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_newApi() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final Api mockApi = mockApi(api);
        
        final Event mockEvent = mockEvent(api);
            
        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(Collections.singleton(mockEvent));
        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        verify(apiManager).deploy(mockApi);
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithSameApi() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final Api mockApi = mockApi(api);
        final Event mockEvent = mockEvent(api);
        
        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(Collections.singleton(mockEvent));
        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));
        when(apiManager.get(api.getId())).thenReturn(null);

        syncManager.refresh();

        when(apiManager.get(api.getId())).thenReturn(mockApi);

        syncManager.refresh();

        verify(apiManager).deploy(mockApi);
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoApis() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();
        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test-2").updatedAt(new Date()).definition("test2").build();

        final Api mockApi = mockApi(api);

        final Api mockApi2 = mockApi(api2);
        
        final Event mockEvent = mockEvent(api);
        
        final Event mockEvent2 = mockEvent(api2);
        
        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(Collections.singleton(mockEvent));
        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        Set<io.gravitee.repository.management.model.Api> apis = new HashSet<>();
        apis.add(api);
        apis.add(api2);
        
        Set<Event> events = new HashSet<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(events);
        when(apiRepository.findAll()).thenReturn(apis);

        syncManager.refresh();

        verify(apiManager, times(2)).deploy(argThat(new ArgumentMatcher<Api>() {
            @Override
            public boolean matches(Object argument) {
                final Api api = (Api) argument;
                return api.getId().equals(mockApi.getId()) || api2.getId().equals(mockApi2.getId());
            }
        }));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoApis_apiToRemove() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();
        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test-2").updatedAt(new Date()).definition("test2").build();

        final Api mockApi = mockApi(api);

        final Api mockApi2 = mockApi(api2);
        
        final Event mockEvent = mockEvent(api);
        
        final Event mockEvent2 = mockEvent(api2);

        Set<Event> events = new HashSet<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(events);
        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        when(apiRepository.findAll()).thenReturn(Collections.singleton(api2));
        when(apiManager.apis()).thenReturn(Collections.singleton(mockApi));

        syncManager.refresh();

        verify(apiManager, times(2)).deploy(argThat(new ArgumentMatcher<Api>() {
            @Override
            public boolean matches(Object argument) {
                final Api api = (Api) argument;
                return api.getId().equals(mockApi.getId()) || api2.getId().equals(mockApi2.getId());
            }
        }));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager).undeploy(api.getId());
        verify(apiManager, never()).undeploy(api2.getId());
    }

    @Test
    public void test_twiceWithTwoApis_apiToUpdate() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        Instant updateDateInst = api.getUpdatedAt().toInstant().plus(Duration.ofHours(1));
        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test").updatedAt(Date.from(updateDateInst)).definition("test2").build();

        final Api mockApi = mockApi(api);
        mockApi(api2);
        
        final Event mockEvent = mockEvent(api);
        
        final Event mockEvent2 = mockEvent(api2);

        Set<Event> events = new HashSet<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(events);
        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        when(apiRepository.findAll()).thenReturn(Collections.singleton(api2));
        when(apiManager.get(api.getId())).thenReturn(mockApi);

        syncManager.refresh();

        verify(apiManager).deploy(mockApi);
        verify(apiManager).update(mockApi);
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoApis_api_noUpdate() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();
        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test").updatedAt(api.getUpdatedAt()).definition("test").build();

        final Api mockApi = mockApi(api);
        
        final Event mockEvent = mockEvent(api);
        
        final Event mockEvent2 = mockEvent(api2);

        Set<Event> events = new HashSet<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(events);

        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        when(apiRepository.findAll()).thenReturn(Collections.singleton(api2));

        syncManager.refresh();

        verify(apiManager, times(2)).deploy(mockApi);
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_throwTechnicalException() throws TechnicalException {
        when(apiRepository.findAll()).thenThrow(TechnicalException.class);

        syncManager.refresh();

        verify(apiManager, never()).deploy(any(Api.class));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_deployApiWithTag() throws Exception {
        shouldDeployApiWithTags(new String[]{"test"});
    }

    @Test
    public void test_deployApiWithUpperCasedTag() throws Exception {
        shouldDeployApiWithTags(new String[]{"Test"});
    }

    @Test
    public void test_deployApiWithAccentTag() throws Exception {
        shouldDeployApiWithTags(new String[]{"tést"});
    }

    @Test
    public void test_deployApiWithUpperCasedAndAccentTag() throws Exception {
        shouldDeployApiWithTags(new String[]{"Tést"});
    }

    public void shouldDeployApiWithTags(final String[] apiTags) throws Exception {
        System.setProperty(SyncManager.TAGS_PROP, "test,toto");

        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final Api mockApi = mockApi(api, apiTags);
        
        final Event mockEvent = mockEvent(api);
        
        when(eventRepository.findByType(Arrays.asList(EventType.PUBLISH_API, EventType.UNPUBLISH_API))).thenReturn(Collections.singleton(mockEvent));
        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        verify(apiManager).deploy(mockApi);
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
        System.clearProperty(SyncManager.TAGS_PROP);
    }

    @Test
    public void test_not_deployApiWithoutTag() throws Exception {
        System.setProperty(SyncManager.TAGS_PROP, "test,toto");

        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final Api mockApi = mockApi(api);

        when(apiRepository.findAll()).thenReturn(Collections.singleton(api));

        syncManager.refresh();

        verify(apiManager, never()).deploy(mockApi);
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
        System.clearProperty(SyncManager.TAGS_PROP);
    }

    private Api mockApi(final io.gravitee.repository.management.model.Api api, final String[] tags) throws Exception {
        final Api mockApi = mockApi(api);
        mockApi.setTags(new HashSet<>(Arrays.asList(tags)));
        return mockApi;
    }

    private Api mockApi(final io.gravitee.repository.management.model.Api api) throws Exception {
        final Api mockApi = new Api();
        mockApi.setId(api.getId());
        mockApi.setDeployedAt(api.getUpdatedAt());
        when(objectMapper.readValue(api.getDefinition(), Api.class)).thenReturn(mockApi);
        return mockApi;
    }
    
    private Event mockEvent(final io.gravitee.repository.management.model.Api api) throws Exception {
		final JsonNodeFactory factory = JsonNodeFactory.instance;
		ObjectNode node = factory.objectNode();
		node.set("id", factory.textNode(api.getId()));

		Event event = new Event();
		event.setType(EventType.PUBLISH_API);
		event.setId(UUID.randomUUID().toString());
		event.setPayload(node.toString());
		event.setCreatedAt(new Date());
		event.setUpdatedAt(event.getCreatedAt());

		when(objectMapper.readTree(event.getPayload())).thenReturn((JsonNode) node);
		when(objectMapper.convertValue((JsonNode) node, io.gravitee.repository.management.model.Api.class)).thenReturn(api);
	
		return event;
    }
}
