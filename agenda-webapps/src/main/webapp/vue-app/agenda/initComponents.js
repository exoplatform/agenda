import Agenda from './components/Agenda.vue';
import AgendaHeader from './components/AgendaHeader.vue';
import AgendaView from './components/AgendaView.vue';
import CreateEventButton from './components/agenda-events/CreateEventButton.vue';
import SyncEventButton from './components/agenda-events/SyncEventButton.vue';
import CreateEventDialog from './components/agenda-events/AgendaEventDialog.vue';
import CreateEventStepper from './components/agenda-events/CreateEventStepper.vue';
import AgendaToolbar from './components/AgendaToolbar.vue';
import AgendaSchedule from './components/AgendaSchedule.vue';
import AgendaEvent from './components/AgendaEvent.vue';
import AgendaSwitchView from './components/AgendaSwitchView.vue';
import AgendaSearchEvent from './components/AgendaSearchEvent.vue';
import AgendaFilterButton from './components/AgendaFilterButton.vue';
import AgendaSettingsButton from './components/AgendaSettingsButton.vue';


const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader,
  'agenda-view': AgendaView,
  'create-event-button': CreateEventButton,
  'sync-event-button':SyncEventButton,
  'create-event-dialog': CreateEventDialog,
  'create-event-stepper': CreateEventStepper,
  'agenda-toolbar': AgendaToolbar,
  'agenda-schedule': AgendaSchedule,
  'agenda-event': AgendaEvent,
  'agenda-switch-view': AgendaSwitchView,
  'agenda-search-event': AgendaSearchEvent,
  'agenda-filter-button': AgendaFilterButton,
  'agenda-settings-button': AgendaSettingsButton
};

for (const key in components) {
  Vue.component(key, components[key]);
}
