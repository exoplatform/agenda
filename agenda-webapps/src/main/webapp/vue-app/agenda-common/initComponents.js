import AgendaHeader from './components/calendar-body/AgendaHeader.vue';
import AgendaBody from './components/calendar-body/AgendaBody.vue';
import AgendaCalendar from './components/calendar-body/AgendaCalendar.vue';
import AgendaToolbar from './components/calendar-body/AgendaToolbar.vue';
import AgendaPreviewEventDialog from './components/calendar-body/AgendaPreviewEventDialog.vue';
import AgendaTimeZoneSelectBox from './components/calendar-body/AgendaTimeZoneSelectBox.vue';
import AgendaEventsUpdater from './components/calendar-body/AgendaEventsUpdater.vue';
import AgendaMobileHeader from './components/calendar-body/mobile/AgendaMobileHeader.vue';
import AgendaTimeline from './components/calendar-body/mobile/AgendaTimeline.vue';
import AgendaEmptyTimeline from './components/calendar-body/mobile/AgendaEmptyTimeline.vue';

import AgendaEventMobileForm from './components/event/form/mobile/AgendaEventMobileForm.vue';

import AgendaCreateEventButton from './components/top-toolbar/AgendaCreateEventButton.vue';
import AgendaCalendarFilterButton from './components/top-toolbar/AgendaCalendarFilterButton.vue';

import AgendaFilterCalendarDrawer from './components/filter/AgendaFilterCalendarDrawer.vue';
import AgendaFilterCalendarList from './components/filter/AgendaFilterCalendarList.vue';
import AgendaFilterCalendarItem from './components/filter/AgendaFilterCalendarItem.vue';
import AgendaFilterCalendarSearch from './components/filter/AgendaFilterCalendarSearch.vue';

import AgendaPendingInvitationDrawer from './components/pending-date-polls/AgendaPendingInvitationDrawer.vue';
import AgendaPendingInvitationItem from './components/pending-date-polls/AgendaPendingInvitationItem.vue';
import AgendaPendingInvitationBadge from './components/pending-date-polls/AgendaPendingInvitationBadge.vue';

import AgendaEventSave from './components/event/AgendaEventSave.vue';
import AgendaEventDialog from './components/event/AgendaEventDialog.vue';

import AgendaEventForm from './components/event/form/AgendaEventForm.vue';
import AgendaEventFormBasicInformation from './components/event/form/AgendaEventFormBasicInformation.vue';
import AgendaEventQuickFormDrawer from './components/event/form/AgendaEventQuickFormDrawer.vue';
import AgendaEventFormDates from './components/event/form/AgendaEventFormDates.vue';
import AgendaEventFormDatePickers from './components/event/form/AgendaEventFormDatePickers.vue';
import AgendaEventFormReminders from './components/event/form/AgendaEventFormReminders.vue';
import AgendaSwitchView from './components/top-toolbar/AgendaSwitchView.vue';
import AgendaEventFormAttachments from './components/event/form/AgendaEventFormAttachments.vue';
import AgendaEventFormAttachmentItem from './components/event/form/AgendaEventFormAttachmentItem.vue';
import AgendaEventFormRecurrence from './components/event/form/AgendaEventFormRecurrence.vue';
import AgendaEventFormRecurrenceDrawer from './components/event/form/AgendaEventFormRecurrenceDrawer.vue';
import AgendaEventFormAttendees from './components/event/form/AgendaEventFormAttendees.vue';
import AgendaEventFormAttendeeItem from './components/event/form/AgendaEventFormAttendeeItem.vue';
import AgendaEventFormCalendarOwner from './components/event/form/AgendaEventFormCalendarOwner.vue';
import AgendaEventFormConference from './components/event/form/AgendaEventFormConference.vue';

import AgendaEventDatePollDetails from './components/event/date-poll/AgendaEventDatePollDetails.vue';
import AgendaEventDatePollDetailsMobile from './components/event/date-poll/mobile/AgendaEventDatePollDetailsMobile.vue';
import AgendaEventDateOptionPeriod from './components/event/date-poll/AgendaEventDateOptionPeriod.vue';
import AgendaEventDateOptionVoter from './components/event/date-poll/AgendaEventDateOptionVoter.vue';
import AgendaEventDateOptionVote from './components/event/date-poll/AgendaEventDateOptionVote.vue';
import AgendaDateOptionConflictDrawer from './components/event/date-poll/AgendaDateOptionConflictDrawer.vue';
import AgendaDateOptionConflictItem from './components/event/date-poll/AgendaDateOptionConflictItem.vue';
import AgendaEventDateOptionPeriodMobile from './components/event/date-poll/mobile/AgendaEventDateOptionPeriodMobile.vue';
import AgendaEventDateOptionVoteMobile from './components/event/date-poll/mobile/AgendaEventDateOptionVoteMobile.vue';
import AgendaEventDatePollItemMobile from './components/event/date-poll/mobile/AgendaEventDatePollItemMobile.vue';
import AgendaDatePollParticipantsDrawerMobile from './components/event/date-poll/mobile/AgendaDatePollParticipantsDrawerMobile.vue';
import AgendaEventDateOptionVoterMobile from './components/event/date-poll/mobile/AgendaEventDateOptionVoterMobile.vue';
import AgendaDatePollActionButtons from './components/event/date-poll/AgendaDatePollActionButtons.vue';
import AgendaEventDatePollDetailsDesktop from './components/event/date-poll/AgendaEventDatePollDetailsDesktop.vue';

import AgendaUserSettingDrawer from './components/settings/AgendaUserSettingDrawer.vue';
import AgendaReminderUserSettings from './components/settings/AgendaReminderUserSettings.vue';
import AgendaReminderUserSettingItem from './components/settings/AgendaReminderUserSettingItem.vue';

import AgendaEventDetails from './components/event/view/AgendaEventDetails.vue';
import AgendaEventsDetailsBody from './components/event/view/AgendaEventsDetailsBody.vue';
import AgendaEventDetailsToolbar from './components/event/view/AgendaEventDetailsToolbar.vue';
import AgendaEventDetailsMobileToolbar from './components/event/view/mobile/AgendaEventDetailsMobileToolbar.vue';
import AgendaDatePollDetailsMobileToolbar from './components/event/date-poll/mobile/AgendaDatePollDetailsMobileToolbar.vue';
import AgendaEventRecurrence from './components/event/view/AgendaEventRecurrence.vue';
import AgendaEventAttendeeButtons from './components/event/view/AgendaEventAttendeeButtons.vue';
import AgendaEventAttendees from './components/event/view/AgendaEventAttendees.vue';
import AgendaEventAttendeeItem from './components/event/view/AgendaEventAttendeeItem.vue';
import AgendaEventRemindersDrawer from './components/event/view/AgendaEventRemindersDrawer.vue';

import AgendaRecurrentEventSaveConfirmDialog from './components/event/confirm-dialog/AgendaRecurrentEventSaveConfirmDialog.vue';
import AgendaRecurrentEventDeleteConfirmDialog from './components/event/confirm-dialog/AgendaRecurrentEventDeleteConfirmDialog.vue';
import AgendaRecurrentEventResponseConfirmDialog from './components/event/confirm-dialog/AgendaRecurrentEventResponseConfirmDialog.vue';
import AgendaRecurrentEventReminderConfirmDialog from './components/event/confirm-dialog/AgendaRecurrentEventReminderConfirmDialog.vue';

import AgendaConnector from './components/connector/AgendaConnector.vue';
import AgendaConnectorStatus from './components/connector/AgendaConnectorStatus.vue';
import AgendaConnectorContemporaryEvents from './components/remote-event/AgendaConnectorContemporaryEvents.vue';
import AgendaConnectorRemoteEventItem from './components/remote-event/AgendaConnectorRemoteEventItem.vue';
import AgendaConnectorsDrawer from './components/remote-event/AgendaConnectorsDrawer.vue';

import AgendaNotificationAlert from './components/snackbar/AgendaNotificationAlert.vue';
import AgendaNotificationAlerts from './components/snackbar/AgendaNotificationAlerts.vue';

const components = {
  'agenda-header': AgendaHeader,
  'agenda-body': AgendaBody,
  'agenda-calendar': AgendaCalendar,
  'agenda-mobile-header': AgendaMobileHeader,
  'agenda-timeline': AgendaTimeline,
  'agenda-empty-timeline': AgendaEmptyTimeline,
  'agenda-events-updater': AgendaEventsUpdater,
  'agenda-event-mobile-form': AgendaEventMobileForm,
  'agenda-filter-calendar-drawer': AgendaFilterCalendarDrawer,
  'agenda-filter-calendar-list': AgendaFilterCalendarList,
  'agenda-filter-calendar-item': AgendaFilterCalendarItem,
  'agenda-filter-calendar-search': AgendaFilterCalendarSearch,
  'agenda-create-event-button': AgendaCreateEventButton,
  'agenda-calendar-filter-button': AgendaCalendarFilterButton,
  'agenda-event-dialog': AgendaEventDialog,
  'agenda-event-form': AgendaEventForm,
  'agenda-toolbar': AgendaToolbar,
  'agenda-event-preview-dialog': AgendaPreviewEventDialog,
  'agenda-event-save': AgendaEventSave,
  'agenda-switch-view': AgendaSwitchView,
  'agenda-event-form-basic-information': AgendaEventFormBasicInformation,
  'agenda-event-quick-form-drawer': AgendaEventQuickFormDrawer,
  'agenda-event-form-dates': AgendaEventFormDates,
  'agenda-event-form-date-pickers': AgendaEventFormDatePickers,
  'agenda-event-form-reminders': AgendaEventFormReminders,
  'agenda-event-form-attachments': AgendaEventFormAttachments,
  'agenda-event-form-attachment-item': AgendaEventFormAttachmentItem,
  'agenda-event-details': AgendaEventDetails,
  'agenda-event-details-body': AgendaEventsDetailsBody,
  'agenda-event-details-toolbar': AgendaEventDetailsToolbar,
  'agenda-event-details-mobile-toolbar': AgendaEventDetailsMobileToolbar,
  'agenda-date-poll-details-mobile-toolbar':AgendaDatePollDetailsMobileToolbar,
  'agenda-event-recurrence': AgendaEventRecurrence,
  'agenda-event-attendees': AgendaEventAttendees,
  'agenda-event-attendee-item': AgendaEventAttendeeItem,
  'agenda-event-attendee-buttons': AgendaEventAttendeeButtons,
  'agenda-event-reminder-drawer': AgendaEventRemindersDrawer,
  'agenda-recurrent-event-delete-confirm-dialog': AgendaRecurrentEventDeleteConfirmDialog,
  'agenda-event-form-recurrence': AgendaEventFormRecurrence,
  'agenda-event-form-recurrence-drawer': AgendaEventFormRecurrenceDrawer,
  'agenda-event-form-attendees': AgendaEventFormAttendees,
  'agenda-event-form-attendee-item': AgendaEventFormAttendeeItem,
  'agenda-event-form-calendar-owner': AgendaEventFormCalendarOwner,
  'agenda-event-form-conference':AgendaEventFormConference,
  'agenda-event-date-poll-details':AgendaEventDatePollDetails,
  'agenda-event-date-poll-details-desktop':AgendaEventDatePollDetailsDesktop,
  'agenda-event-date-poll-details-mobile':AgendaEventDatePollDetailsMobile,
  'agenda-event-date-option-period':AgendaEventDateOptionPeriod,
  'agenda-event-date-option-voter':AgendaEventDateOptionVoter,
  'agenda-event-date-option-vote':AgendaEventDateOptionVote,
  'agenda-event-date-poll-item-mobile': AgendaEventDatePollItemMobile,
  'agenda-event-date-option-vote-mobile': AgendaEventDateOptionVoteMobile,
  'agenda-event-date-option-period-mobile':AgendaEventDateOptionPeriodMobile,
  'agenda-event-date-option-voter-mobile':AgendaEventDateOptionVoterMobile,
  'agenda-date-poll-action-buttons':AgendaDatePollActionButtons,
  'agenda-date-poll-participants-drawer-mobile':AgendaDatePollParticipantsDrawerMobile,
  'agenda-recurrent-event-save-confirm-dialog': AgendaRecurrentEventSaveConfirmDialog,
  'agenda-recurrent-event-response-confirm-dialog': AgendaRecurrentEventResponseConfirmDialog,
  'agenda-recurrent-event-reminders-confirm-dialog': AgendaRecurrentEventReminderConfirmDialog,
  'agenda-user-setting-drawer': AgendaUserSettingDrawer,
  'agenda-connectors-drawer': AgendaConnectorsDrawer,
  'agenda-pending-invitation-drawer':AgendaPendingInvitationDrawer,
  'agenda-pending-invitation-item':AgendaPendingInvitationItem,
  'agenda-pending-invitation-badge':AgendaPendingInvitationBadge,
  'agenda-reminder-user-settings': AgendaReminderUserSettings,
  'agenda-reminder-user-setting-item': AgendaReminderUserSettingItem,
  'agenda-date-option-conflict-drawer':AgendaDateOptionConflictDrawer,
  'agenda-date-option-conflict-item':AgendaDateOptionConflictItem,
  'agenda-connector': AgendaConnector,
  'agenda-connector-status': AgendaConnectorStatus,
  'agenda-connector-contemporary-events': AgendaConnectorContemporaryEvents,
  'agenda-connector-remote-event-item': AgendaConnectorRemoteEventItem,
  'agenda-time-zone-select-box': AgendaTimeZoneSelectBox,
  'agenda-notification-alert': AgendaNotificationAlert,
  'agenda-notification-alerts': AgendaNotificationAlerts,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

import * as eventService from './js/EventService.js';
import * as calendarService from './js/CalendarService.js';
import * as settingsService from './js/SettingsService.js';
import * as agendaUtils from './js/AgendaUtils.js';
import * as datePollUtils from './js/DatePollUtils.js';
import * as webConferencingService from './js/EventWebConferencingService.js';
import * as remoteEventConnector from './js/RemoteEventConnector.js';
import * as agendaWebSocket from './js/WebSocket.js';

const userTimeZoneId = agendaUtils.USER_TIMEZONE_ID;

if (!Vue.prototype.$calendarService) {
  window.Object.defineProperty(Vue.prototype, '$calendarService', {
    value: calendarService,
  });
}
if (!Vue.prototype.$eventService) {
  window.Object.defineProperty(Vue.prototype, '$eventService', {
    value: eventService,
  });
}
if (!Vue.prototype.$settingsService) {
  window.Object.defineProperty(Vue.prototype, '$settingsService', {
    value: settingsService,
  });
}
if (!Vue.prototype.$agendaUtils) {
  window.Object.defineProperty(Vue.prototype, '$agendaUtils', {
    value: agendaUtils,
  });
}
if (!Vue.prototype.$datePollUtils) {
  window.Object.defineProperty(Vue.prototype, '$datePollUtils', {
    value: datePollUtils,
  });
}
if (!Vue.prototype.$userTimeZoneId) {
  window.Object.defineProperty(Vue.prototype, '$userTimeZoneId', {
    value: userTimeZoneId,
  });
}
if (!Vue.prototype.$webConferencingService) {
  window.Object.defineProperty(Vue.prototype, '$webConferencingService', {
    value: webConferencingService,
  });
}
if (!Vue.prototype.$remoteEventConnector) {
  window.Object.defineProperty(Vue.prototype, '$remoteEventConnector', {
    value: remoteEventConnector,
  });
}
if (!Vue.prototype.$agendaWebSocket) {
  window.Object.defineProperty(Vue.prototype, '$agendaWebSocket', {
    value: agendaWebSocket,
  });
}
