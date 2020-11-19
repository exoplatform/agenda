import AgendaConnector from './components/AgendaConnector.vue';
import AgendaConnectorDetailsButton from './components/AgendaConnectorDetailsButton.vue';

const components = {
  'agenda-connector': AgendaConnector,
  'agenda-connector-details-button': AgendaConnectorDetailsButton,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

// get override components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('AgendaConnector');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}
