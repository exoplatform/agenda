import AgendaUserSettings from './components/AgendaUserSettings.vue';

const components = {
  'agenda-user-settings': AgendaUserSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
