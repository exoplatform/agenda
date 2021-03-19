<template>
  <div class="event-details-body overflow-auto flex-grow-1 d-flex flex-column flex-md-row pa-4">
    <div class="flex-grow-1 flex-shrink-0 d-flex event-details-body-left">
      <div class="mx-auto">
        <div class="event-date align-center d-flex pb-5">
          <i class="uiIconDatePicker darkGreyIcon uiIcon32x32 pr-5"></i>
          <div class="d-inline-flex">
            <date-format
              :value="event.startDate"
              :format="fullDateFormat"
              class="mr-1" />
            <template v-if="!sameDayDates">
              -
              <date-format
                :value="event.endDate"
                :format="fullDateFormat"
                class="ml-1" />
            </template>
          </div>
        </div>
        <div class="event-time align-center d-flex pb-5">
          <i class="uiIconClock darkGreyIcon uiIcon32x32 pr-5"></i>
          <div class="d-inline-flex">
            <template v-if="event.allDay">
              {{ $t('agenda.allDay') }}
            </template>
            <template v-else>
              <date-format
                :value="event.startDate"
                :format="dateTimeFormat"
                class="mr-1" />
              -
              <date-format
                :value="event.endDate"
                :format="dateTimeFormat"
                class="ml-1 mr-2" />
            </template>
            <template>
              ( {{ timeZoneName }} )
            </template>
          </div>
        </div>
        <div v-if="hasRecurrence" class="event-recurrence align-center d-flex pl-1 pb-5 text-truncate">
          <i class="uiIconRefresh darkGreyIcon uiIcon32x32 pr-5"></i>
          <agenda-event-recurrence :event="event" class="text-wrap text-left" />
        </div>
        <div v-if="canAddReminders" class="event-reminders align-center d-flex pb-5 text-truncate">
          <i class="uiIcon32x32 notifIcon darkGreyIcon pr-5 mt-1 mb-auto"></i>
          <v-list
            class="py-0 text-truncate"
            dense>
            <v-list-item v-if="!event.reminders || !event.reminders.length">
              <label class="text-sub-title font-italic mx-auto">
                {{ $t('agenda.noRemindersYet') }}
              </label>
            </v-list-item>
            <template v-else>
              <v-list-item
                v-for="(reminder, index) in event.reminders"
                :key="index"
                class="pl-0"
                dense>
                <v-chip
                  class="mt-1 mb-2 mr-2"
                  color="primary"
                  outlined>
                  <span class="text--primary text-truncate">
                    <template v-if="reminder.before">
                      {{ $t('agenda.label.notifyMeBefore', {0: reminder.before, 1: $t(`agenda.option.${reminder.beforePeriodType.toLowerCase()}s`).toLowerCase()}) }}
                    </template>
                    <template v-else>
                      {{ $t('agenda.label.notifyMeWhenEventStarts') }}
                    </template>
                  </span>
                </v-chip>
              </v-list-item>
            </template>
          </v-list>
          <v-btn
            icon
            transparent
            class="mb-auto border-box-sizing"
            @click="$refs.reminders.open()">
            <i class="uiIconEditInfo uiIcon16x16 darkGreyIcon pt-2"></i>
          </v-btn>
        </div>
        <div v-if="isConferenceEnabled" class="event-conference d-flex flex-grow-0 flex-shrink-1 pb-5">
          <i class="uiIconVideo darkGreyIcon uiIcon32x32 pr-5"></i>
          <v-btn
            link
            text
            :href="eventConferenceUrl"
            class="text-lowercase primary--text">
            {{ eventConferenceUrl }}
          </v-btn>
        </div>
        <div v-if="event.location" class="event-location d-flex flex-grow-0 flex-shrink-1 pb-5">
          <i class="uiIconCheckin darkGreyIcon uiIcon32x32 pr-5"></i>
          <span v-autolinker="event.location" class="align-self-center text-break"></span>
        </div>
        <div v-if="event.description" class="event-description d-flex flex-grow-0 flex-shrink-1 pb-5">
          <i class="uiIconDescription darkGreyIcon uiIcon32x32 pr-5"></i>
          <span v-autolinker:[autolinkerArgs]="event.description" class="mt-1 align-self-center text-wrap text-left text-break"></span>
        </div>
        <div
          v-if="event.attachments && event.attachments.length !== 0"
          class="event-attachments align-center d-flex pb-5">
          <i class="uiIconAttach darkGreyIcon uiIcon32x32 pr-5"></i>
          <div
            v-for="attachedFile in event.attachments"
            :key="attachedFile.name"
            class="uploadedFilesItem">
            <div class="showFile">
              <exo-attachment-item :file="attachedFile" />
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="flex-grow-0 mx-5 d-none d-md-block">
      <v-divider vertical class="event-details-body-divider" />
    </div>
    <div class="flex-grow-1 flex-shrink-0 d-flex event-details-body-right">
      <div class="mx-auto">
        <agenda-event-attendees
          ref="agendaAttendees"
          :event="event" />
        <agenda-connector-contemporary-events
          :settings="settings"
          :event="event"
          :connectors="connectors"
          :class="!isAcceptedEvent && 'agenda-hidden-connectors'"
          class="mt-5" />
      </div>
    </div>
    <agenda-event-reminder-drawer
      v-if="canAddReminders"
      ref="reminders"
      :event="event" />
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
    connectors: {
      type: Array,
      default: () => []
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
  },
  data() {
    return {
      fullDateFormat: {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      },
      dateDayFormat: {
        month: 'short',
        day: 'numeric',
      },
      dateTimeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
      timeZoneName: null,
    };
  },
  computed: {
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    isConferenceEnabled() {
      return this.eventConferenceType === 'manual' || (this.conferenceProvider && this.eventConferenceType && this.conferenceProvider.getType() === this.eventConferenceType);
    },
    eventConferences() {
      return this.event && this.event.conferences;
    },
    eventConference() {
      // For now, use only one web conference per event
      return this.eventConferences
          && this.eventConferences.length
          && this.eventConferences[0];
    },
    eventConferenceType() {
      return this.eventConference && this.eventConference.type;
    },
    eventConferenceUrl() {
      return this.eventConference && this.eventConference.url;
    },
    calendarOwnerLink() {
      if (this.owner) {
        if (this.owner.providerId === 'organization') {
          return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.owner.remoteId}`;
        } else if (this.owner.providerId === 'space') {
          return `${eXo.env.portal.context}/g/:spaces:${this.owner.remoteId}/`;
        }
      }
      return '';
    },
    hasRecurrence() {
      return this.event.recurrence || this.event.parent && this.event.parent.recurrence;
    },
    isAttendee() {
      return this.event.acl && this.event.acl.attendee;
    },
    isAcceptedEvent() {
      if (!this.isAttendee) {
        return false;
      }
      const currentUserResponse = this.event.attendees.find(attendee => attendee && attendee.identity.remoteId ===  eXo.env.portal.userName);
      return currentUserResponse && currentUserResponse.response !== 'DECLINED';
    },
    owner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    ownerProfile() {
      return this.owner && (this.owner.profile || this.owner.space);
    },
    ownerAvatarUrl() {
      return this.ownerProfile && (this.ownerProfile.avatar || this.ownerProfile.avatarUrl);
    },
    ownerDisplayName() {
      return this.ownerProfile && (this.ownerProfile.displayName || this.ownerProfile.fullname || this.ownerProfile.fullName);
    },
    sameDayDates() {
      return this.event.startDate && this.event.endDate && this.$agendaUtils.areDatesOnSameDay(this.event.startDate, this.event.endDate);
    },
    canAddReminders() {
      if (!this.isAttendee) {
        return false;
      }
      const currentUserResponse = this.event.attendees.find(attendee => attendee && attendee.identity.remoteId ===  eXo.env.portal.userName);
      return currentUserResponse && (currentUserResponse.response === 'ACCEPTED' || currentUserResponse.response === 'TENTATIVE');
    },
    acceptedResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'ACCEPTED').length;
    },
    refusedResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'DECLINED').length;
    },
    tentativeResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'TENTATIVE').length;
    },
    needsActionResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'NEEDS_ACTION').length;
    },
    attendeesResponsesTitle() {
      return this.$t('agenda.attendeesResponsesOverview', {
        0: this.acceptedResponsesCount,
        1: this.refusedResponsesCount,
        2: this.needsActionResponsesCount,
        3: this.tentativeResponsesCount,
      });
    },
  },
  created() {
    this.timeZoneName = this.$agendaUtils.getTimeZoneNameFromTimeZoneId(this.$agendaUtils.USER_TIMEZONE_ID);
  },
  methods: {
    closeDialog() {
      this.$emit('close');
    },
  }
};
</script>