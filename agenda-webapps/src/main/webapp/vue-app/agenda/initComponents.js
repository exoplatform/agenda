import Agenda from './components/calendar-body/Agenda.vue';
import AgendaHeader from './components/calendar-body/AgendaHeader.vue';
import AgendaBody from './components/calendar-body/AgendaBody.vue';
import AgendaCalendar from './components/calendar-body/AgendaCalendar.vue';
import AgendaCreateEventButton from './components/top-toolbar/AgendaCreateEventButton.vue';
import SyncEventButton from './components/top-toolbar/AgendaSyncEventButton.vue';
import AgendaEventDialog from './components/event/AgendaEventDialog.vue';
import AgendaEventForm from './components/event/AgendaEventForm.vue';
import AgendaEventFormBasicInformation from './components/event/AgendaEventFormBasicInformation.vue';
import AgendaEventFormDates from './components/event/AgendaEventFormDates.vue';
import AgendaEventFormReminders from './components/event/AgendaEventFormReminders.vue';
import AgendaToolbar from './components/calendar-body/AgendaToolbar.vue';
import AgendaPreviewEventDialog from './components/calendar-body/AgendaPreviewEventDialog.vue';
import AgendaCalendarOwnersFilterDrawer from './components/top-toolbar/AgendaCalendarOwnersFilterDrawer.vue';
import AgendaSwitchView from './components/top-toolbar/AgendaSwitchView.vue';
import AgendaSearchEvent from './components/top-toolbar/AgendaSearchEvent.vue';
import AgendaEventFormAttachments from './components/event/AgendaEventFormAttachments.vue';
import AgendaEventFormAttachmentItem from './components/event/AgendaEventFormAttachmentItem.vue';
import AgendaEventDetails from './components/event/AgendaEventDetails.vue';
import AgendaEventAttendees from './components/event/attendee/AgendaEventAttendees.vue';
import AgendaEventAttendeeItem from './components/event/attendee/AgendaEventAttendeeItem.vue';
import AgendaSettingsDrawer from './components/settings/AgendaSettingsDrawer.vue';
import AgendaCalendarsTab from './components/settings/AgendaCalendarsTab.vue';
import AgendaCalendarsItem from './components/settings/AgendaCalendarsItem.vue';
import AgendaSettingsTab from './components/settings/AgendaSettingsTab.vue';
import AgendaSyncTab from './components/settings/AgendaSyncTab.vue';
import AgendaRecurrentEventDeleteConfirmDialog from './components/event/AgendaRecurrentEventDeleteConfirmDialog.vue';
import AgendaCustomRecurrenceDrawer from './components/event/AgendaCustomRecurrenceDrawer.vue';

const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader,
  'agenda-body': AgendaBody,
  'agenda-calendar': AgendaCalendar,
  'agenda-calendar-owners-filter-drawer': AgendaCalendarOwnersFilterDrawer,
  'agenda-create-event-button': AgendaCreateEventButton,
  'agenda-sync-event-button':SyncEventButton,
  'agenda-event-dialog': AgendaEventDialog,
  'agenda-event-form': AgendaEventForm,
  'agenda-toolbar': AgendaToolbar,
  'agenda-event-preview-dialog': AgendaPreviewEventDialog,
  'agenda-switch-view': AgendaSwitchView,
  'agenda-search-event': AgendaSearchEvent,
  'agenda-event-form-basic-information': AgendaEventFormBasicInformation,
  'agenda-event-form-dates': AgendaEventFormDates,
  'agenda-event-form-reminders': AgendaEventFormReminders,
  'agenda-event-form-attachments': AgendaEventFormAttachments,
  'agenda-event-form-attachment-item': AgendaEventFormAttachmentItem,
  'agenda-event-details': AgendaEventDetails,
  'agenda-event-attendees': AgendaEventAttendees,
  'agenda-event-attendee-item': AgendaEventAttendeeItem,
  'agenda-settings-drawer': AgendaSettingsDrawer,
  'agenda-calendars-tab': AgendaCalendarsTab,
  'agenda-calendars-item': AgendaCalendarsItem,
  'agenda-settings-tab': AgendaSettingsTab,
  'agenda-sync-tab': AgendaSyncTab,
  'agenda-recurrent-event-delete-confirm-dialog': AgendaRecurrentEventDeleteConfirmDialog,
  'agenda-custom-recurrence-drawer': AgendaCustomRecurrenceDrawer
};

for (const key in components) {
  Vue.component(key, components[key]);
}
