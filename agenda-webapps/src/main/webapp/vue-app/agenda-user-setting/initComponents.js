import AgendaUserSettings from './components/AgendaUserSettings.vue';
import AgendaUserGeneralSettings from './components/AgendaUserGeneralSettings.vue';

const components = {
  'agenda-user-settings': AgendaUserSettings,
  'agenda-user-general-settings': AgendaUserGeneralSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
