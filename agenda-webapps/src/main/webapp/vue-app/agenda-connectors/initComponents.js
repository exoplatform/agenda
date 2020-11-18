import AgendaConnector from './components/AgendaConnector.vue';

const components = {
  'agenda-connector': AgendaConnector,
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
