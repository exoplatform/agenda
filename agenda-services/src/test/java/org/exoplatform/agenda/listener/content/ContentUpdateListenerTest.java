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

import java.util.*;

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
  public void shouldCreateMetadataWhenNoExistingItem() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    org.exoplatform.agenda.model.Event agendaEvent =
        mock(org.exoplatform.agenda.model.Event.class);

    when(agendaEvent.getCreatorId()).thenReturn(1L);
    when(agendaEventService.getEventById(123L)).thenReturn(agendaEvent);

    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(Collections.emptyList());

    contentUpdateListener.onEvent(event);

    verify(metadataService).createMetadataItem(
        any(MetadataObject.class),
        eq(EVENT_METADATA_KEY),
        anyMap(),
        eq(1L)
    );

    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
  }

  @Test
  public void shouldUpdateMetadataWhenContentChanged() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    org.exoplatform.agenda.model.Event agendaEvent =
        mock(org.exoplatform.agenda.model.Event.class);

    when(agendaEvent.getModifierId()).thenReturn(2L);
    when(agendaEventService.getEventById(123L)).thenReturn(agendaEvent);

    MetadataItem item = mock(MetadataItem.class);

    Map<String, String> props = new HashMap<>();
    props.put("contentId", "000");

    when(item.getProperties()).thenReturn(props);

    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(List.of(item));

    contentUpdateListener.onEvent(event);

    verify(metadataService).updateMetadataItem(eq(item), eq(2L));
    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldNotUpdateWhenContentUnchanged() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    org.exoplatform.agenda.model.Event agendaEvent =
        mock(org.exoplatform.agenda.model.Event.class);

    when(agendaEventService.getEventById(123L)).thenReturn(agendaEvent);

    MetadataItem item = mock(MetadataItem.class);

    Map<String, String> props = new HashMap<>();
    props.put("contentId", "999");

    when(item.getProperties()).thenReturn(props);

    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(List.of(item));

    contentUpdateListener.onEvent(event);

    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldRemoveContentIdOnDelete() throws Exception {
    when(event.getEventName()).thenReturn(DELETE_NEWS);
    when(event.getData()).thenReturn(news);

    MetadataItem item = mock(MetadataItem.class);

    Map<String, String> props = new HashMap<>();
    props.put("contentId", "999");

    when(item.getProperties()).thenReturn(props);

    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(List.of(item));

    contentUpdateListener.onEvent(event);

    assertFalse(item.getProperties().containsKey("contentId"));

    verify(metadataService).updateMetadataItem(eq(item), anyLong());
  }

  @Test
  public void shouldDoNothingWhenNoMetadataOnDelete() throws Exception {
    when(event.getEventName()).thenReturn(DELETE_NEWS);
    when(event.getData()).thenReturn(news);

    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any()))
        .thenReturn(Collections.emptyList());

    contentUpdateListener.onEvent(event);

    verify(metadataService, never()).updateMetadataItem(any(), anyLong());
    verify(metadataService, never()).createMetadataItem(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldIgnoreUnrelatedEvents() throws Exception {
    when(event.getEventName()).thenReturn("OTHER_EVENT");

    contentUpdateListener.onEvent(event);

    verifyNoInteractions(metadataService);
    verifyNoInteractions(agendaEventService);
  }

  @Test
  public void shouldNotProcessWhenParametersNull() throws Exception {
    news.setParameters(null);

    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    contentUpdateListener.onEvent(event);

    verifyNoInteractions(metadataService);
  }

  @Test
  public void shouldNotProcessWhenEventIdMissing() throws Exception {
    news.setParameters(new HashMap<>());

    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    contentUpdateListener.onEvent(event);

    verifyNoInteractions(metadataService);
  }

  @Test
  public void shouldNotProcessWhenAgendaEventNotFound() throws Exception {
    when(event.getEventName()).thenReturn(UPDATE_NEWS);
    when(event.getData()).thenReturn(news);

    when(agendaEventService.getEventById(123L)).thenReturn(null);

    contentUpdateListener.onEvent(event);

    verifyNoInteractions(metadataService);
  }
}
