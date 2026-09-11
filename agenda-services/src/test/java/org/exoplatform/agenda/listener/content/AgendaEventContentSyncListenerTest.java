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

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.exoplatform.agenda.util.Utils.POST_UPDATE_AGENDA_EVENT_EVENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link AgendaEventContentSyncListener}, focused on the
 * metadata-properties guard (EXO-90192). The listener is built by hand with a
 * {@code null} {@code NewsService}: that class cannot be mocked from this
 * module (its method signatures reference the notes model, absent from the
 * test classpath, and putting {@code notes-service} on it breaks the kernel
 * container tests of the module), and every path exercised here returns
 * before the news service is reached.
 */
@ExtendWith(MockitoExtension.class)
public class AgendaEventContentSyncListenerTest {

  private static final long EVENT_ID = 123L;

  @Mock
  private ListenerService                        listenerService;

  @Mock
  private AgendaEventService                     agendaEventService;

  @Mock
  private MetadataService                        metadataService;

  @Mock
  private IdentityManager                        identityManager;

  @Mock
  private Event<AgendaEventModification, Object> event;

  @Mock
  private AgendaEventModification                agendaEventModification;

  private AgendaEventContentSyncListener         listener;

  /**
   * Builds the listener under test; see the class comment for the
   * {@code null} news service.
   */
  @BeforeEach
  public void setUp() {
    listener = new AgendaEventContentSyncListener(listenerService,
                                                  agendaEventService,
                                                  metadataService,
                                                  null,
                                                  identityManager);
  }

  /**
   * Wires the listener event to a modification of {@link #EVENT_ID}; called
   * by the tests that reach {@code onEvent} only, to keep strict stubbing
   * happy.
   */
  private void stubEventModification() {
    when(event.getSource()).thenReturn(agendaEventModification);
    when(agendaEventModification.getEventId()).thenReturn(EVENT_ID);
  }

  /**
   * The listener registers itself on the event update broadcast only.
   */
  @Test
  public void shouldRegisterListenerOnInit() {
    listener.init();

    verify(listenerService).addListener(POST_UPDATE_AGENDA_EVENT_EVENT, listener);
    verifyNoMoreInteractions(listenerService);
  }

  /**
   * Regression pin for EXO-90192: a metadata item whose properties map is
   * {@code null} — what social returns for an item with no stored property,
   * i.e. every event saved without parameters — must be skipped, not
   * dereferenced.
   */
  @Test
  public void shouldIgnoreMetadataItemWithNullProperties() throws Exception {
    stubEventModification();
    MetadataItem item = new MetadataItem();
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any())).thenReturn(List.of(item));

    listener.onEvent(event);

    verifyNoInteractions(agendaEventService, identityManager);
  }

  /**
   * A metadata item carrying properties other than the content link is not a
   * linked event either.
   */
  @Test
  public void shouldIgnoreMetadataItemWithoutContentId() throws Exception {
    stubEventModification();
    MetadataItem item = new MetadataItem();
    item.setProperties(Map.of("other", "value"));
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any())).thenReturn(List.of(item));

    listener.onEvent(event);

    verifyNoInteractions(agendaEventService, identityManager);
  }
}
