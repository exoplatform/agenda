import Agenda from './components/Agenda.vue';
import AgendaHeader from './components/AgendaHeader.vue';
import AgendaBody from './components/AgendaBody.vue';
import AgendaCalendar from './components/AgendaCalendar.vue';
import CreateEventButton from './components/CreateEventButton.vue';
import SyncEventButton from './components/SyncEventButton.vue';
import AgendaEventDialog from './components/AgendaEventDialog.vue';
import AgendaEventForm from './components/AgendaEventForm.vue';
import AgendaToolbar from './components/AgendaToolbar.vue';
import AgendaPreviewEventDialog from './components/AgendaPreviewEventDialog.vue';
import AgendaSwitchView from './components/AgendaSwitchView.vue';
import AgendaSearchEvent from './components/AgendaSearchEvent.vue';
import AgendaFilterButton from './components/AgendaFilterButton.vue';
import AgendaSettingsButton from './components/AgendaSettingsButton.vue';
import AgendaPullDownEvent from './components/AgendaPullDownEvent.vue';

const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader,
  'agenda-body': AgendaBody,
  'agenda-calendar': AgendaCalendar,
  'create-event-button': CreateEventButton,
  'sync-event-button':SyncEventButton,
  'agenda-event-dialog': AgendaEventDialog,
  'agenda-event-form': AgendaEventForm,
  'agenda-toolbar': AgendaToolbar,
  'agenda-event-preview-dialog': AgendaPreviewEventDialog,
  'agenda-switch-view': AgendaSwitchView,
  'agenda-search-event': AgendaSearchEvent,
  'agenda-filter-button': AgendaFilterButton,
  'agenda-settings-button': AgendaSettingsButton,
  'agenda-pulldown-event': AgendaPullDownEvent
};

for (const key in components) {
  Vue.component(key, components[key]);
}
