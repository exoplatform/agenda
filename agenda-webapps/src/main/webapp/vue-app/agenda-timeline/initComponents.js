import AgendaTimelineWidget from './components/AgendaTimelineWidget.vue';

const components = {
  'agenda-timeline-widget': AgendaTimelineWidget,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
