import Agenda from './components/Agenda.vue';
import AgendaHeader from './components/calendar-body/AgendaHeader.vue';
import AgendaBody from './components/calendar-body/AgendaBody.vue';
import AgendaCalendar from './components/calendar-body/AgendaCalendar.vue';
import AgendaToolbar from './components/calendar-body/AgendaToolbar.vue';
import AgendaPreviewEventDialog from './components/calendar-body/AgendaPreviewEventDialog.vue';

import AgendaCreateEventButton from './components/top-toolbar/AgendaCreateEventButton.vue';
import AgendaCalendarOwnersFilterDrawer from './components/top-toolbar/AgendaCalendarOwnersFilterDrawer.vue';
import SyncEventButton from './components/top-toolbar/AgendaSyncEventButton.vue';

import AgendaEventDialog from './components/event/AgendaEventDialog.vue';

import AgendaEventForm from './components/event/form/AgendaEventForm.vue';
import AgendaEventFormBasicInformation from './components/event/form/AgendaEventFormBasicInformation.vue';
import AgendaEventFormDates from './components/event/form/AgendaEventFormDates.vue';
import AgendaEventFormReminders from './components/event/form/AgendaEventFormReminders.vue';
import AgendaSwitchView from './components/top-toolbar/AgendaSwitchView.vue';
import AgendaSearchEvent from './components/top-toolbar/AgendaSearchEvent.vue';
import AgendaEventFormAttachments from './components/event/form/AgendaEventFormAttachments.vue';
import AgendaEventFormAttachmentItem from './components/event/form/AgendaEventFormAttachmentItem.vue';
import AgendaEventFormRecurrence from './components/event/form/AgendaEventFormRecurrence.vue';
import AgendaEventFormRecurrenceDrawer from './components/event/form/AgendaEventFormRecurrenceDrawer.vue';
import AgendaEventFormAttendees from './components/event/form/AgendaEventFormAttendees.vue';
import AgendaEventFormAttendeeItem from './components/event/form/AgendaEventFormAttendeeItem.vue';
import AgendaEventFormCalendarOwner from './components/event/form/AgendaEventFormCalendarOwner.vue';
import AgendaRecurrentEventSaveConfirmDialog from './components/event/form/AgendaRecurrentEventSaveConfirmDialog.vue';

import AgendaEventDetails from './components/event/view/AgendaEventDetails.vue';
import AgendaEventAttendees from './components/event/view/AgendaEventAttendees.vue';
import AgendaEventAttendeeItem from './components/event/view/AgendaEventAttendeeItem.vue';
import AgendaRecurrentEventDeleteConfirmDialog from './components/event/view/AgendaRecurrentEventDeleteConfirmDialog.vue';
import AgendaRecurrentEventResponseConfirmDialog from './components/event/view/AgendaRecurrentEventResponseConfirmDialog.vue';

import AgendaSettingsDrawer from './components/settings/AgendaSettingsDrawer.vue';
import AgendaCalendarsTab from './components/settings/AgendaCalendarsTab.vue';
import AgendaCalendarsItem from './components/settings/AgendaCalendarsItem.vue';
import AgendaSettingsTab from './components/settings/AgendaSettingsTab.vue';
import AgendaSyncTab from './components/settings/AgendaSyncTab.vue';

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
  'agenda-event-form-recurrence': AgendaEventFormRecurrence,
  'agenda-event-form-recurrence-drawer': AgendaEventFormRecurrenceDrawer,
  'agenda-event-form-attendees': AgendaEventFormAttendees,
  'agenda-event-form-attendee-item': AgendaEventFormAttendeeItem,
  'agenda-event-form-calendar-owner': AgendaEventFormCalendarOwner,
  'agenda-recurrent-event-save-confirm-dialog': AgendaRecurrentEventSaveConfirmDialog,
  'agenda-recurrent-event-response-confirm-dialog': AgendaRecurrentEventResponseConfirmDialog,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
