import AgendaAdminSettings from './components/AgendaAdminSettings.vue';
import AgendaAdminConnectorSettings from './components/AgendaAdminConnectorSettings.vue';

const components = {
  'agenda-admin-settings': AgendaAdminSettings,
  'agenda-admin-connector-settings': AgendaAdminConnectorSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
