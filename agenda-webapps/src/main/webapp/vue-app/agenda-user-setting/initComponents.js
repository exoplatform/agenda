import AgendaUserSettings from './components/AgendaUserSettings.vue';
import AgendaUserGeneralSettings from './components/AgendaUserGeneralSettings.vue';
import AgendaUserConnectorSettings from './components/AgendaUserConnectorSettings.vue';

const components = {
  'agenda-user-settings': AgendaUserSettings,
  'agenda-user-general-settings': AgendaUserGeneralSettings,
  'agenda-user-connector-settings': AgendaUserConnectorSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
