package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.*;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.space.SpaceException;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent.Type;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaSpaceApplicationListenerTest {

  protected static final AtomicBoolean LISTENER_INSTALL_COUNT   = new AtomicBoolean(false);

  protected static final AtomicBoolean LISTENER_UNINSTALL_COUNT = new AtomicBoolean(false);

  protected static PortalContainer     container;

  protected static SpaceService        spaceService;

  @BeforeClass
  public static void setUpAllTests() {
    container = PortalContainer.getInstance();
    spaceService = container.getComponentInstanceOfType(SpaceService.class);
    ListenerService listenerService = container.getComponentInstanceOfType(ListenerService.class);

    listenerService.addListener(AgendaSpaceApplicationListener.AGENDA_APPLICATION_INSTALLED_EVENT_NAME,
                                new Listener<Space, Type>() {
                                  @Override
                                  public void onEvent(Event<Space, Type> event) throws Exception {
                                    LISTENER_INSTALL_COUNT.set(true);
                                  }
                                });

    listenerService.addListener(AgendaSpaceApplicationListener.AGENDA_APPLICATION_UNINSTALLED_EVENT_NAME,
                                new Listener<Space, Type>() {
                                  @Override
                                  public void onEvent(Event<Space, Type> event) throws Exception {
                                    LISTENER_UNINSTALL_COUNT.set(true);
                                  }
                                });
  }

  @Before
  public void setUp() {
    LISTENER_INSTALL_COUNT.set(false);
    LISTENER_UNINSTALL_COUNT.set(false);

    ConversationState.setCurrent(new ConversationState(new Identity("root", Collections.emptyList())));
    begin();
  }

  @After
  public void tearDown() throws Exception {
    ListAccess<Space> allSpacesListAccess = spaceService.getAllSpacesWithListAccess();
    int size = allSpacesListAccess.getSize();
    if (size > 0) {
      Space[] spaces = allSpacesListAccess.load(0, size);
      for (Space space : spaces) {
        spaceService.deleteSpace(space);
      }
    }
    ConversationState.setCurrent(null);
    end();
  }

  @Test
  public void testCreateSpaceWithDefaultSpaceTemplate() throws SpaceException {
    Space space = createSpace("Space Test Classic", null, "root");

    assertFalse(LISTENER_INSTALL_COUNT.get());
    assertFalse(LISTENER_UNINSTALL_COUNT.get());

    spaceService.activateApplication(space, AgendaSpaceApplicationListener.AGENDA_AGENDA_PORTLET_ID);

    assertTrue(LISTENER_INSTALL_COUNT.get());
    assertFalse(LISTENER_UNINSTALL_COUNT.get());

    LISTENER_INSTALL_COUNT.set(false);
    spaceService.removeApplication(space,
                                   AgendaSpaceApplicationListener.AGENDA_AGENDA_PORTLET_ID,
                                   AgendaSpaceApplicationListener.AGENDA_APPLICATION_NAME);

    assertFalse(LISTENER_INSTALL_COUNT.get());
    assertTrue(LISTENER_UNINSTALL_COUNT.get());
  }

  @Test
  public void testCreateSpaceWithAgendaSpaceTemplate() {
    createSpace("Space Test with Agenda app Template", "agendaTemplate", "root");

    assertTrue(LISTENER_INSTALL_COUNT.get());
    assertFalse(LISTENER_UNINSTALL_COUNT.get());
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  protected Space createSpace(String displayName, String spaceTemplate, String... members) {
    Space newSpace = new Space();
    newSpace.setDisplayName(displayName);
    newSpace.setPrettyName(displayName);
    newSpace.setManagers(new String[] { "root" });
    newSpace.setMembers(members);
    newSpace.setRegistration(Space.OPEN);
    newSpace.setVisibility(Space.PRIVATE);
    newSpace.setTemplate(spaceTemplate);
    return spaceService.createSpace(newSpace, "root");
  }
}
