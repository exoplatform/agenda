import Agenda from './components/Agenda.vue';
import AgendaHeader from './components/calendar-body/AgendaHeader.vue';
import AgendaBody from './components/calendar-body/AgendaBody.vue';
import AgendaCalendar from './components/calendar-body/AgendaCalendar.vue';
import AgendaToolbar from './components/calendar-body/AgendaToolbar.vue';
import AgendaPreviewEventDialog from './components/calendar-body/AgendaPreviewEventDialog.vue';

import AgendaCreateEventButton from './components/top-toolbar/AgendaCreateEventButton.vue';

import AgendaFilterCalendarDrawer from './components/filter/AgendaFilterCalendarDrawer.vue';
import AgendaFilterCalendarList from './components/filter/AgendaFilterCalendarList.vue';
import AgendaFilterCalendarItem from './components/filter/AgendaFilterCalendarItem.vue';
import AgendaFilterCalendarSearch from './components/filter/AgendaFilterCalendarSearch.vue';

import AgendaEventDialog from './components/event/AgendaEventDialog.vue';

import AgendaEventForm from './components/event/form/AgendaEventForm.vue';
import AgendaEventFormBasicInformation from './components/event/form/AgendaEventFormBasicInformation.vue';
import AgendaEventFormDates from './components/event/form/AgendaEventFormDates.vue';
import AgendaEventFormReminders from './components/event/form/AgendaEventFormReminders.vue';
import AgendaSwitchView from './components/top-toolbar/AgendaSwitchView.vue';
import AgendaEventFormAttachments from './components/event/form/AgendaEventFormAttachments.vue';
import AgendaEventFormAttachmentItem from './components/event/form/AgendaEventFormAttachmentItem.vue';
import AgendaEventFormRecurrence from './components/event/form/AgendaEventFormRecurrence.vue';
import AgendaEventFormRecurrenceDrawer from './components/event/form/AgendaEventFormRecurrenceDrawer.vue';
import AgendaEventFormAttendees from './components/event/form/AgendaEventFormAttendees.vue';
import AgendaEventFormAttendeeItem from './components/event/form/AgendaEventFormAttendeeItem.vue';
import AgendaEventFormCalendarOwner from './components/event/form/AgendaEventFormCalendarOwner.vue';
import AgendaRecurrentEventSaveConfirmDialog from './components/event/form/AgendaRecurrentEventSaveConfirmDialog.vue';

import AgendaEventDetails from './components/event/view/AgendaEventDetails.vue';
import AgendaEventRecurrence from './components/event/view/AgendaEventRecurrence.vue';
import AgendaEventAttendeeButtons from './components/event/view/AgendaEventAttendeeButtons.vue';
import AgendaEventAttendees from './components/event/view/AgendaEventAttendees.vue';
import AgendaEventAttendeeItem from './components/event/view/AgendaEventAttendeeItem.vue';
import AgendaRecurrentEventDeleteConfirmDialog from './components/event/view/AgendaRecurrentEventDeleteConfirmDialog.vue';
import AgendaRecurrentEventResponseConfirmDialog from './components/event/view/AgendaRecurrentEventResponseConfirmDialog.vue';

import UserSettingAgenda from './components/user-setting-agenda/UserSettingAgenda.vue';
import UserSettingAgendaDrawer from './components/user-setting-agenda/UserSettingAgendaDrawer.vue';

const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader,
  'agenda-body': AgendaBody,
  'agenda-calendar': AgendaCalendar,
  'agenda-filter-calendar-drawer': AgendaFilterCalendarDrawer,
  'agenda-filter-calendar-list': AgendaFilterCalendarList,
  'agenda-filter-calendar-item': AgendaFilterCalendarItem,
  'agenda-filter-calendar-search': AgendaFilterCalendarSearch,
  'agenda-create-event-button': AgendaCreateEventButton,
  'agenda-event-dialog': AgendaEventDialog,
  'agenda-event-form': AgendaEventForm,
  'agenda-toolbar': AgendaToolbar,
  'agenda-event-preview-dialog': AgendaPreviewEventDialog,
  'agenda-switch-view': AgendaSwitchView,
  'agenda-event-form-basic-information': AgendaEventFormBasicInformation,
  'agenda-event-form-dates': AgendaEventFormDates,
  'agenda-event-form-reminders': AgendaEventFormReminders,
  'agenda-event-form-attachments': AgendaEventFormAttachments,
  'agenda-event-form-attachment-item': AgendaEventFormAttachmentItem,
  'agenda-event-details': AgendaEventDetails,
  'agenda-event-recurrence': AgendaEventRecurrence,
  'agenda-event-attendees': AgendaEventAttendees,
  'agenda-event-attendee-item': AgendaEventAttendeeItem,
  'agenda-event-attendee-buttons': AgendaEventAttendeeButtons,
  'agenda-recurrent-event-delete-confirm-dialog': AgendaRecurrentEventDeleteConfirmDialog,
  'agenda-event-form-recurrence': AgendaEventFormRecurrence,
  'agenda-event-form-recurrence-drawer': AgendaEventFormRecurrenceDrawer,
  'agenda-event-form-attendees': AgendaEventFormAttendees,
  'agenda-event-form-attendee-item': AgendaEventFormAttendeeItem,
  'agenda-event-form-calendar-owner': AgendaEventFormCalendarOwner,
  'agenda-recurrent-event-save-confirm-dialog': AgendaRecurrentEventSaveConfirmDialog,
  'agenda-recurrent-event-response-confirm-dialog': AgendaRecurrentEventResponseConfirmDialog,
  'user-setting-agenda': UserSettingAgenda,
  'user-setting-agenda-drawer': UserSettingAgendaDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
