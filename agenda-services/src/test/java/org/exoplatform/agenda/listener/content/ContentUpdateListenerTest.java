/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.listener.content;

import io.meeds.content.news.model.News;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.meeds.content.news.utils.NewsUtils.*;
import static org.exoplatform.agenda.util.Utils.EVENT_METADATA_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContentUpdateListenerTest {

  @Mock
  private ListenerService listenerService;

  @Mock
  private AgendaEventService agendaEventService;

  @Mock
  private MetadataService metadataService;

  @Mock
  private Event<String, News> event;

  @InjectMocks
  private ContentUpdateListener contentUpdateListener;

  private News news;

  @BeforeEach
  public void setUp() {
    news = new News();
    Map<String, String> params = new HashMap<>();
    params.put("eventId", "123");
    news.setParameters(params);
    news.setId("999");
  }

  @Test
  public void shouldRegisterListenersOnInit() {
    contentUpdateListener.init();

    verify(listenerService).addListener(UPDATE_NEWS, contentUpdateListener);
    verify(listenerService).addListener(DELETE_NEWS, contentUpdateListener);
    verifyNoMoreInteractions(listenerService);
  }

  @Test
  public void shouldCreateMetadataWhenUpdateNewsWithValidEventId() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    org.exoplatform.agenda.model.Event agendaEvent = mock(org.exoplatform.agenda.model.Event.class);
    when(agendaEvent.getCreatorId()).thenReturn(1L);
    when(agendaEventService.getEventById(123L)).thenReturn(agendaEvent);
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any())).thenReturn(new java.util.ArrayList<>());

    contentUpdateListener.onEvent(event);

    ArgumentCaptor<MetadataObject> metadataCaptor = ArgumentCaptor.forClass(MetadataObject.class);
    ArgumentCaptor<Map<String, String>> propsCaptor = ArgumentCaptor.forClass(Map.class);

    verify(metadataService).createMetadataItem(
        metadataCaptor.capture(),
        eq(EVENT_METADATA_KEY),
        propsCaptor.capture(),
        eq(1L)
    );

    MetadataObject metadata = metadataCaptor.getValue();
    Map<String, String> props = propsCaptor.getValue();
    assertEquals("agendaEvent", metadata.getType());
    assertEquals("123", metadata.getId());
    assertEquals("999", props.get("contentId"));
  }

  @Test
  public void shouldUpdateMetadataWhenContentIdChanged() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    org.exoplatform.agenda.model.Event agendaEvent = mock(org.exoplatform.agenda.model.Event.class);
    when(agendaEvent.getModifierId()).thenReturn(1L);
    when(agendaEventService.getEventById(123L)).thenReturn(agendaEvent);

    MetadataItem existingItem = mock(MetadataItem.class);
    Map<String, String> existingProps = new HashMap<>();
    existingProps.put("contentId", "000"); // different from news.getId() "999"
    when(existingItem.getProperties()).thenReturn(existingProps);
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(List.of(existingItem));

    contentUpdateListener.onEvent(event);

    verify(metadataService).updateMetadataItem(eq(existingItem), eq(1L));
    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldNotUpdateMetadataWhenContentIdUnchanged() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    org.exoplatform.agenda.model.Event agendaEvent = mock(org.exoplatform.agenda.model.Event.class);
    when(agendaEventService.getEventById(123L)).thenReturn(agendaEvent);

    MetadataItem existingItem = mock(MetadataItem.class);
    Map<String, String> existingProps = new HashMap<>();
    existingProps.put("contentId", "999"); // same as news.getId()
    when(existingItem.getProperties()).thenReturn(existingProps);
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(List.of(existingItem));

    contentUpdateListener.onEvent(event);

    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldNotProcessWhenParametersNull() throws Exception {
    news.setParameters(null);
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    contentUpdateListener.onEvent(event);

    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
  }

  @Test
  public void shouldNotProcessWhenEventIdMissing() throws Exception {
    news.setParameters(new HashMap<>());
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    contentUpdateListener.onEvent(event);

    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
  }

  @Test
  public void shouldNotProcessWhenAgendaEventNotFound() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);
    when(agendaEventService.getEventById(123L)).thenReturn(null);

    contentUpdateListener.onEvent(event);

    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
  }

  @Test
  public void shouldIgnoreUnrelatedEvents() throws Exception {
    when(event.getEventName()).thenReturn("someOtherEvent");
    when(event.getData()).thenReturn(news);

    contentUpdateListener.onEvent(event);

    verifyNoInteractions(metadataService);
    verifyNoInteractions(agendaEventService);
  }
}
