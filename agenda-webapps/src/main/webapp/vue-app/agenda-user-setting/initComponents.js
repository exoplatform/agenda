import AgendaUserSettings from './components/AgendaUserSettings.vue';
import AgendaUserSettingDrawer from '../agenda/components/settings/AgendaUserSettingDrawer.vue';

const components = {
  'agenda-user-settings': AgendaUserSettings,
  'agenda-user-setting-drawer': AgendaUserSettingDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
