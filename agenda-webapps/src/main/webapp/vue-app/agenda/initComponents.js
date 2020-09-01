import Agenda from './components/calendar-body/Agenda.vue';
import AgendaHeader from './components/calendar-body/AgendaHeader.vue';
import AgendaBody from './components/calendar-body/AgendaBody.vue';
import AgendaCalendar from './components/calendar-body/AgendaCalendar.vue';
import AgendaCreateEventButton from './components/top-toolbar/AgendaCreateEventButton.vue';
import SyncEventButton from './components/top-toolbar/AgendaSyncEventButton.vue';
import AgendaEventDialog from './components/event/AgendaEventDialog.vue';
import AgendaEventForm from './components/event/AgendaEventForm.vue';
import AgendaToolbar from './components/calendar-body/AgendaToolbar.vue';
import AgendaPreviewEventDialog from './components/calendar-body/AgendaPreviewEventDialog.vue';
import AgendaSwitchView from './components/top-toolbar/AgendaSwitchView.vue';
import AgendaSearchEvent from './components/top-toolbar/AgendaSearchEvent.vue';
import AgendaFilterButton from './components/top-toolbar/AgendaFilterButton.vue';
import AgendaSettingsButton from './components/top-toolbar/AgendaSettingsButton.vue';
import AgendaPullDownEvent from './components/top-toolbar/AgendaPullDownEvent.vue';
import AgendaEventDetailsForm from './components/event/AgendaEventDetailsForm.vue';
import AgendaFileAttachments from './components/event/AgendaFileAttachments.vue';

const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader,
  'agenda-body': AgendaBody,
  'agenda-calendar': AgendaCalendar,
  'agenda-create-event-button': AgendaCreateEventButton,
  'agenda-sync-event-button':SyncEventButton,
  'agenda-event-dialog': AgendaEventDialog,
  'agenda-event-form': AgendaEventForm,
  'agenda-toolbar': AgendaToolbar,
  'agenda-event-preview-dialog': AgendaPreviewEventDialog,
  'agenda-switch-view': AgendaSwitchView,
  'agenda-search-event': AgendaSearchEvent,
  'agenda-filter-button': AgendaFilterButton,
  'agenda-settings-button': AgendaSettingsButton,
  'agenda-pulldown-event': AgendaPullDownEvent,
  'agenda-event-details-form': AgendaEventDetailsForm,
  'agenda-file-attachments': AgendaFileAttachments
};

for (const key in components) {
  Vue.component(key, components[key]);
}
