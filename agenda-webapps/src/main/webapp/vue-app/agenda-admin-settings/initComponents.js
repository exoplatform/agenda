import AgendaAdminSettings from './components/AgendaAdminSettings.vue';

const components = {
  'agenda-admin-settings': AgendaAdminSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
