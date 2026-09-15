import AgendaUserSettings from './components/AgendaUserSettings.vue';
import AgendaUserGeneralSettings from './components/AgendaUserGeneralSettings.vue';
import AgendaUserConnectorSettings from './components/AgendaUserConnectorSettings.vue';
import AgendaUserRemoteConnectorsSettings from './components/AgendaUserRemoteConnectorsSettings.vue';
import AgendaUserPushSettings from './components/AgendaUserPushSettings.vue';
import AgendaUserAvailabilitySharingSettings from './components/AgendaUserAvailabilitySharingSettings.vue';
import AgendaUserPublishedCalendarsSettings from './components/AgendaUserPublishedCalendarsSettings.vue';
import AgendaUserPublishedCalendarsDrawer from './components/AgendaUserPublishedCalendarsDrawer.vue';

const components = {
  'agenda-user-settings': AgendaUserSettings,
  'agenda-user-general-settings': AgendaUserGeneralSettings,
  'agenda-user-connector-settings': AgendaUserConnectorSettings,
  'agenda-user-remote-connectors-settings': AgendaUserRemoteConnectorsSettings,
  'agenda-user-push-settings': AgendaUserPushSettings,
  'agenda-user-availability-sharing-settings': AgendaUserAvailabilitySharingSettings,
  'agenda-user-published-calendars-settings': AgendaUserPublishedCalendarsSettings,
  'agenda-user-published-calendars-drawer': AgendaUserPublishedCalendarsDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
