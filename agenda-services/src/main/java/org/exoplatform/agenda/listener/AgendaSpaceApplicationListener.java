package org.exoplatform.agenda.listener;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.Safe;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.*;
import org.exoplatform.portal.config.serialize.PortletApplication;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.navigation.*;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.pom.spi.portlet.Portlet;
import org.exoplatform.portal.webui.application.PortletState;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.SpaceTemplate;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.*;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent.Type;

public class AgendaSpaceApplicationListener implements SpaceLifeCycleListener {

  private static final String[] AGENDA_TIMELINE_APPLICATION_ACCESS_PERMISSIONS = new String[] { "Everyone" };

  public static final String    AGENDA_APPLICATION_INSTALLED_EVENT_NAME        = "agenda.space.application.installed";

  public static final String    AGENDA_APPLICATION_UNINSTALLED_EVENT_NAME      = "agenda.space.application.uniinstalled";

  public static final String    SPACE_HOME_EXTENSIBLE_CONTAINER_ID             = "SpaceHomePortlets";

  public static final String    AGENDA_APPLICATION_NAME                        = "agenda";

  public static final String    AGENDA_AGENDA_PORTLET_ID                       = "Agenda";

  public static final String    AGENDA_AGENDA_TIMELINE_PORTLET_ID              = "AgendaTimeline";

  public static final String    AGENDA_AGENDA_COMPLETE_ID                      =
                                                          AGENDA_APPLICATION_NAME + "/" + AGENDA_AGENDA_PORTLET_ID;

  public static final String    AGENDA_AGENDA_TIMELINE_COMPLETE_ID             =
                                                                   AGENDA_APPLICATION_NAME + "/"
                                                                       + AGENDA_AGENDA_TIMELINE_PORTLET_ID;

  private static final Log      LOG                                            =
                                    ExoLogger.getLogger(AgendaSpaceApplicationListener.class);

  private ListenerService       listenerService;

  private SpaceTemplateService  spaceTemplateService;

  private IdentityManager       identityManager;

  private NavigationService     navigationService;

  private NavigationStore       navigationStore;

  private DataStorage           dataStorage;

  private AgendaEventService     agendaEventService ;

  private AgendaEventConferenceService agendaEventConferenceService ;

  private AgendaEventAttendeeService agendaEventAttendeeService ;

  private AgendaEventDatePollService agendaEventDatePollService ;

  private AgendaEventReminderService agendaEventReminderService;

  @Override
  public void applicationAdded(SpaceLifeCycleEvent event) {
    installAgendaApplication(event);
  }

  @Override
  public void applicationActivated(SpaceLifeCycleEvent event) {
    installAgendaApplication(event);
  }

  @Override
  public void applicationRemoved(SpaceLifeCycleEvent event) {
    uninstallAgendaApplication(event);
  }

  @Override
  public void applicationDeactivated(SpaceLifeCycleEvent event) {
    uninstallAgendaApplication(event);
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceRemoved(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void joined(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void left(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    // Not needed
    Type eventType = event.getType();
    if (eventType == Type.SPACE_RENAMED) {
      Space space = event.getSpace();
      String[] spaceManagers = space.getManagers();
      for (String spaceManager:spaceManagers) {
      Identity identity = getIdentityManager().getOrCreateIdentity(OrganizationIdentityProvider.NAME, spaceManager);
      long userIdentityId = identity == null ? 0 : Long.parseLong(identity.getId());
      EventFilter eventFilter = new EventFilter();
      ZonedDateTime start = ZonedDateTime.now();
      ZonedDateTime end = ZonedDateTime.now().plusMonths(1);
      eventFilter.setStart(start);
      eventFilter.setEnd(end);
      ZoneId zone = ZoneOffset.UTC;
      try {
        List<Event> events = getEventService().getEvents(eventFilter, zone, userIdentityId);

        for (Event agendaEvent : events) {
          List<EventConference> conferences = getEventConferenceService().getEventConferences(agendaEvent.getId());
          for (EventConference eventConference : conferences) {
            int startIndex = (eventConference.getUrl().indexOf("_")) + 1;
            int indIndex = eventConference.getUrl().indexOf("-");
            String OldSpaceName = eventConference.getUrl().substring(startIndex, indIndex);
            eventConference.setUrl(eventConference.getUrl().replace(OldSpaceName, space.getPrettyName()));
          }
          try {
            Event updatedEvent = agendaEventService.updateEvent(agendaEvent,
                    (List<EventAttendee>) getAgendaEventAttendeeService().getEventAttendees(agendaEvent.getId()),
                    conferences,
                    getAgendaEventReminderService().getEventReminders(agendaEvent.getId()),
                    getAgendaEventDatePollService().getEventDateOptions(agendaEvent.getId(), zone),
                    null,
                    true,
                    userIdentityId
            );
          } catch (ObjectNotFoundException | AgendaException e) {
            LOG.error("Event with id={} can't be updated", agendaEvent.getId());
          }
        }
      } catch (IllegalAccessException e) {
        LOG.error("this user with id={} has no events", userIdentityId);
      }
     }
    }
  }

  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceAccessEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void addInvitedUser(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void addPendingUser(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceBannerEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  private void installAgendaApplication(SpaceLifeCycleEvent event) {
    String appId = event.getTarget();
    if (StringUtils.isNotBlank(appId) && StringUtils.equals(getPortletId(appId), AGENDA_AGENDA_PORTLET_ID)) {
      Space space = event.getSpace();
      try {
        installAgendaApplication(space, event.getType());
      } catch (Exception e) {
        LOG.warn("Error installing AgendaTimeline widget in space {}", space.getDisplayName(), e);
      }
    }
  }

  private void installAgendaApplication(Space space, Type type) throws Exception {
    boolean applicationExists = timelineApplicationAddedInHomePage(space);
    if (!applicationExists) {
      Page spaceHomePage = getSpaceHomePage(space);
      if (spaceHomePage == null) {
        return;
      }

      Container extensibleSpaceHomeContainer = getExtensibleSpaceHomeContainer(spaceHomePage.getChildren());
      if (extensibleSpaceHomeContainer != null) {
        extensibleSpaceHomeContainer.getChildren().add(getAgendaTimelinePortletModel());
        getDataStorage().save(spaceHomePage);
        Utils.broadcastEvent(getListenerService(), AGENDA_APPLICATION_INSTALLED_EVENT_NAME, space, type);
      }
    }
  }

  private void uninstallAgendaApplication(SpaceLifeCycleEvent event) {
    String appId = event.getTarget();
    if (StringUtils.isNotBlank(appId) && StringUtils.equals(getPortletId(appId), AGENDA_AGENDA_PORTLET_ID)) {
      try {
        uninstallAgendaApplication(event.getSpace(), event.getType());
      } catch (Exception e) {
        LOG.warn("Error uninstalling AgendaTimeline widget from space {}", event.getSpace().getDisplayName(), e);
      }
    }
  }

  private boolean timelineApplicationAddedInHomePage(Space space) throws Exception {
    Page page = getSpaceHomePage(space);
    if (page == null) {
      LOG.info("Can't find home page content of space '{}', the widget AgendaTimeline will not be installed in Space Home page",
               space.getDisplayName());
      return false;
    }

    ArrayList<ModelObject> childObjects = page.getChildren();
    return timeLineApplicationExists(childObjects);
  }

  private void uninstallAgendaApplication(Space space, Type type) throws Exception {
    Page spaceHomePage = getSpaceHomePage(space);
    if (spaceHomePage == null) {
      return;
    }

    Container extensibleSpaceHomeContainer = getExtensibleSpaceHomeContainer(spaceHomePage.getChildren());
    if (extensibleSpaceHomeContainer != null) {
      ArrayList<ModelObject> children = extensibleSpaceHomeContainer.getChildren();
      boolean removed = removeAgendaTimelineApplication(children);
      if (removed) {
        getDataStorage().save(spaceHomePage);
        Utils.broadcastEvent(getListenerService(), AGENDA_APPLICATION_UNINSTALLED_EVENT_NAME, space, type);
      }
    }
  }

  private boolean timeLineApplicationExists(ArrayList<ModelObject> childObjects) throws Exception {
    for (ModelObject modelObject : childObjects) {
      if (modelObject instanceof Container) {
        ArrayList<ModelObject> subChildren = ((Container) modelObject).getChildren();
        return timeLineApplicationExists(subChildren);
      } else if (modelObject instanceof Application) {
        Application<?> application = (Application<?>) modelObject;
        ApplicationState<?> state = application.getState();
        String applicationId = getDataStorage().getId(state);
        if (StringUtils.equals(AGENDA_AGENDA_TIMELINE_COMPLETE_ID, applicationId)) {
          return true;
        }
      }
    }
    return false;
  }

  private Container getExtensibleSpaceHomeContainer(ArrayList<ModelObject> childObjects) throws Exception {
    if (childObjects == null || childObjects.isEmpty()) {
      return null;
    }
    for (ModelObject modelObject : childObjects) {
      if (modelObject instanceof Container) {
        Container container = (Container) modelObject;
        if (StringUtils.equals(SPACE_HOME_EXTENSIBLE_CONTAINER_ID, container.getId())) {
          return container;
        }
        Container extensibleContainer = getExtensibleSpaceHomeContainer(container.getChildren());
        if (extensibleContainer != null) {
          return extensibleContainer;
        }
      }
    }
    return null;
  }

  private boolean removeAgendaTimelineApplication(ArrayList<ModelObject> childObjects) throws Exception {
    if (childObjects == null || childObjects.isEmpty()) {
      return false;
    }
    boolean removed = false;
    Iterator<ModelObject> childObjectsIterator = childObjects.iterator();
    while (childObjectsIterator.hasNext()) {
      ModelObject modelObject = childObjectsIterator.next();
      if (modelObject instanceof Application) {
        Application<?> application = (Application<?>) modelObject;
        ApplicationState<?> state = application.getState();
        String applicationId = getDataStorage().getId(state);
        if (StringUtils.equals(AGENDA_AGENDA_TIMELINE_COMPLETE_ID, applicationId)) {
          childObjectsIterator.remove();
          removed = true;
        }
      } else if (modelObject instanceof Container) {
        ArrayList<ModelObject> subChildren = ((Container) modelObject).getChildren();
        removed = removeAgendaTimelineApplication(subChildren);
      }
    }
    return removed;
  }

  private Page getSpaceHomePage(Space space) throws Exception {
    Page homePage = getSpaceHomePageBySpaceTemplate(space);
    if (homePage != null) {
      return homePage;
    }
    return getSpaceHomePageFromNavigation(space);
  }

  private Page getSpaceHomePageFromNavigation(Space space) throws Exception {
    String spaceDisplayName = space.getDisplayName();
    NavigationContext navigation = getNavigationService().loadNavigation(SiteKey.group(space.getGroupId()));
    if (navigation == null || navigation.getData() == null || navigation.getData().getRootId() == null) {
      LOG.debug("Can't find home page of space '{}', the widget AgendaTimeline will not be installed in Space Home page",
                spaceDisplayName);
      return null;
    }
    String rootId = navigation.getData().getRootId();
    PageKey homePageKey = getHomePageRef(rootId, spaceDisplayName);
    if (homePageKey == null) {
      LOG.debug("Can't find home page reference of space '{}', the widget AgendaTimeline will not be installed in Space Home page",
                spaceDisplayName);
      return null;
    }
    return getDataStorage().getPage(homePageKey.format());
  }

  private Page getSpaceHomePageBySpaceTemplate(Space space) throws Exception {
    SpaceTemplate spaceTemplate = getSpaceTemplateService().getSpaceTemplateByName(space.getTemplate());
    if (spaceTemplate != null && spaceTemplate.getSpaceHomeApplication() != null) {
      String homePageName = spaceTemplate.getSpaceHomeApplication().getPortletName();
      PageKey homePageKey = new PageKey(SiteKey.group(space.getGroupId()), homePageName);
      return getDataStorage().getPage(homePageKey.format());
    }
    return null;
  }

  private PageKey getHomePageRef(String rootId, String spaceDisplayName) {
    NodeData node = getNavigationStore().loadNode(Safe.parseLong(rootId));
    if (node == null) {
      LOG.debug("Can't find home page of space '{}', the widget AgendaTimeline will not be installed in Space Home page",
                spaceDisplayName);
      return null;
    }

    if (node.getState() != null && node.getState().getPageRef() != null) {
      return node.getState().getPageRef();
    }

    Iterator<String> nodes = node.iterator(false);
    while (nodes.hasNext()) {
      String childId = nodes.next();
      PageKey homePageKey = getHomePageRef(childId, spaceDisplayName);
      if (homePageKey != null) {
        return homePageKey;
      }
    }
    return null;
  }

  private String getPortletId(String appId) {
    final char SEPARATOR = '.';

    if (appId.indexOf(SEPARATOR) != -1) {
      int beginIndex = appId.lastIndexOf(SEPARATOR) + 1;
      int endIndex = appId.length();

      return appId.substring(beginIndex, endIndex);
    }

    return appId;
  }

  private NavigationStore getNavigationStore() {
    if (navigationStore == null) {
      navigationStore = ExoContainerContext.getService(NavigationStore.class);
    }
    return navigationStore;
  }

  private NavigationService getNavigationService() {
    if (navigationService == null) {
      navigationService = ExoContainerContext.getService(NavigationService.class);
    }
    return navigationService;
  }

  private DataStorage getDataStorage() {
    if (dataStorage == null) {
      dataStorage = ExoContainerContext.getService(DataStorage.class);
    }
    return dataStorage;
  }

  private ListenerService getListenerService() {
    if (listenerService == null) {
      listenerService = ExoContainerContext.getService(ListenerService.class);
    }
    return listenerService;
  }

  private SpaceTemplateService getSpaceTemplateService() {
    if (spaceTemplateService == null) {
      spaceTemplateService = ExoContainerContext.getService(SpaceTemplateService.class);
    }
    return spaceTemplateService;
  }

  private static Application<Portlet> getAgendaTimelinePortletModel() {
    PortletApplication model = new PortletApplication();
    PortletState<Portlet> state = new PortletState<>(new TransientApplicationState<Portlet>(AGENDA_AGENDA_TIMELINE_COMPLETE_ID),
                                                     ApplicationType.PORTLET);
    model.setState(state.getApplicationState());
    model.setTitle(AGENDA_AGENDA_TIMELINE_PORTLET_ID);
    model.setShowInfoBar(false);
    model.setShowApplicationState(false);
    model.setShowApplicationMode(false);
    model.setAccessPermissions(AGENDA_TIMELINE_APPLICATION_ACCESS_PERMISSIONS);
    model.setModifiable(true);
    return model;
  }
  private IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = CommonsUtils.getService(IdentityManager.class);
    }
    return identityManager;
  }
  private AgendaEventService getEventService(){
    if(agendaEventService == null) {
      agendaEventService = CommonsUtils.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }
  private AgendaEventConferenceService getEventConferenceService () {
    if( agendaEventConferenceService == null) {
      agendaEventConferenceService = CommonsUtils.getService(AgendaEventConferenceService.class);
    }
    return agendaEventConferenceService;
  }
  private AgendaEventAttendeeService getAgendaEventAttendeeService () {
    if( agendaEventAttendeeService == null) {
      agendaEventAttendeeService = CommonsUtils.getService(AgendaEventAttendeeService.class);
    }
    return agendaEventAttendeeService;
  }
  private AgendaEventDatePollService getAgendaEventDatePollService () {
    if (agendaEventDatePollService == null) {
      agendaEventDatePollService = CommonsUtils.getService(AgendaEventDatePollService.class);
    }
    return agendaEventDatePollService ;
  }
  private AgendaEventReminderService getAgendaEventReminderService () {
    if (agendaEventReminderService == null ) {
      agendaEventReminderService = CommonsUtils.getService(AgendaEventReminderService.class);
    }
    return agendaEventReminderService;
  }
}
