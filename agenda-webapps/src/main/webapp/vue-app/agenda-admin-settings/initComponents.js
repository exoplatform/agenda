import AgendaAdminSettings from './components/AgendaAdminSettings.vue';
import AgendaAdminConnectorSettings from './components/AgendaAdminConnectorSettings.vue';
import AgendaAdminConnectorDrawer from './components/AgendaAdminConnectorDrawer.vue';
import AgendaAdminEmbedMapSettings from './components/AgendaAdminEmbedMapSettings.vue';


const components = {
  'agenda-admin-settings': AgendaAdminSettings,
  'agenda-admin-connector-settings': AgendaAdminConnectorSettings,
  'agenda-admin-connector-drawer': AgendaAdminConnectorDrawer,
  'agenda-admin-embed-map-settings': AgendaAdminEmbedMapSettings
};

for (const key in components) {
  Vue.component(key, components[key]);
}
