import Agenda from './components/Agenda.vue';
import AgendaHeader from './components/AgendaHeader.vue';
import AgendaView from './components/AgendaView.vue';
import CreateEventButton from './components/agenda-events/CreateEventButton.vue';
import CreateEvent from './components/agenda-events/CreateEventDialog.vue';

const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader,
  'agenda-view': AgendaView,
  'create-event-button': CreateEventButton,
  'create-event': CreateEvent
};

for (const key in components) {
  Vue.component(key, components[key]);
}
