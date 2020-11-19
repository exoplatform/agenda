import AgendaConnector from './components/connector/AgendaConnector.vue';
import AgendaConnectorStatus from './components/connector/AgendaConnectorStatus.vue';
import AgendaConnectorContemporaryEvents from './components/remote-event/AgendaConnectorContemporaryEvents.vue';
import AgendaConnectorRemoteEventItem from './components/remote-event/AgendaConnectorRemoteEventItem.vue';

const components = {
  'agenda-connector': AgendaConnector,
  'agenda-connector-status': AgendaConnectorStatus,
  'agenda-connector-contemporary-events': AgendaConnectorContemporaryEvents,
  'agenda-connector-remote-event-item': AgendaConnectorRemoteEventItem,
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
