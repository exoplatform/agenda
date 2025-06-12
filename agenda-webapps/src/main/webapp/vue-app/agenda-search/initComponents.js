import AgendaSearchCard from './components/AgendaSearchCard.vue';
import AgendaEventRecurrence from '../agenda-common/components/event/view/AgendaEventRecurrence.vue';

const components = {
  'agenda-search-card': AgendaSearchCard,
  'agenda-event-recurrence': AgendaEventRecurrence
};

for (const key in components) {
  Vue.component(key, components[key]);
}

// get override components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('agendaSearchCard');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}
