import AgendaTimelineWidget from './components/AgendaTimelineWidget.vue';
import AgendaTimelineHeader from './components/AgendaTimelineHeader.vue';
import AgendaTimelineSettingsDrawer from './components/AgendaTimelineSettingsDrawer.vue';

const components = {
  'agenda-timeline-widget': AgendaTimelineWidget,
  'agenda-timeline-header': AgendaTimelineHeader,
  'agenda-timeline-settings-drawer': AgendaTimelineSettingsDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
