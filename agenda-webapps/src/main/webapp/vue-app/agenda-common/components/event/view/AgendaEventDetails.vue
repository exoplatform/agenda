<template>
  <v-card
    v-if="event"
    flat
    class="event-details d-flex flex-column">
    <agenda-event-details-mobile-toolbar
      v-if="isMobile"
      :event="event"
      @close="$emit('close')"
      @edit="$root.$emit('agenda-event-form', event)"
      @delete="deleteConfirmDialog" />
    <agenda-event-details-toolbar
      v-else
      :event="event"
      @close="$emit('close')"
      @edit="$root.$emit('agenda-event-form', event)"
      @delete="deleteConfirmDialog" />

    <v-divider class="flex-grow-0" />
    <v-progress-linear :active="loading" indeterminate />

    <div class="event-details-body overflow-auto flex-grow-1 d-flex flex-column flex-md-row pa-4">
      <div class="flex-grow-1 flex-shrink-0 event-details-body-left">
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
                class="ml-1" />
            </template>
          </div>
        </div>
        <div v-if="hasRecurrence" class="event-recurrence align-center d-flex pl-1 pb-5 text-truncate">
          <i class="uiIconRefresh darkGreyIcon uiIcon32x32 pr-5"></i>
          <agenda-event-recurrence :event="event" class="text-wrap text-left" />
        </div>
        <div v-if="isAttendee || (event.reminders && event.reminders.length)" class="event-reminders align-center d-flex pb-5 text-truncate">
          <i class="uiIcon32x32 notifIcon darkGreyIcon pr-5 mt-1 mb-auto"></i>
          <v-list
            v-if="event.reminders && event.reminders.length"
            class="py-0"
            dense>
            <v-list-item
              v-for="(reminder, index) in event.reminders"
              :key="index"
              class="pl-0"
              dense>
              <v-chip
                class="ma-2"
                color="primary"
                outlined>
                <span class="text--primary">
                  <template v-if="reminder.before">
                    {{ $t('agenda.label.notifyMeBefore', {0: reminder.before, 1: $t(`agenda.option.${reminder.beforePeriodType.toLowerCase()}s`).toLowerCase()}) }}
                  </template>
                  <template v-else>
                    {{ $t('agenda.label.notifyMeWhenEventStarts') }}
                  </template>
                </span>
              </v-chip>
            </v-list-item>
          </v-list>
          <v-btn
            icon
            transparent
            class="mb-auto"
            @click="$refs.reminders.open()">
            <i class="uiIconEditInfo uiIcon16x16 darkGreyIcon pt-3"></i>
          </v-btn>
        </div>
        <div v-if="event.location" class="event-location d-flex flex-grow-0 flex-shrink-1 pb-5">
          <i class="uiIconCheckin darkGreyIcon uiIcon32x32 pr-5"></i>
          <span v-autolinker="event.location" class="align-self-center"></span>
        </div>
        <div v-if="event.description" class="event-description d-flex flex-grow-0 flex-shrink-1 pb-5">
          <i class="uiIconDescription darkGreyIcon uiIcon32x32 pr-5"></i>
          <span v-autolinker="event.description" class="mt-1 align-self-center text-wrap text-left"></span>
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
      <div class="flex-grow-0 mx-5 d-none d-md-block">
        <v-divider vertical />
      </div>
      <div class="flex-grow-1 flex-shrink-0 event-details-body-right">
        <agenda-event-attendees
          ref="agendaAttendees"
          :event="event" />
        <agenda-connector-contemporary-events
          :settings="settings"
          :event="event"
          :connectors="connectors"
          :connected-account="connectedAccount"
          class="mt-5" />
      </div>
    </div>
    <template v-if="isAttendee">
      <v-divider />
      <agenda-event-attendee-buttons
        ref="eventAttendeeButtons"
        :event="event"
        :class="isMobile && 'mr-1' || 'mr-10'"
        class="flex-grow-0 my-2" />
    </template>
    <agenda-recurrent-event-delete-confirm-dialog
      v-if="event.occurrence"
      ref="deleteConfirmDialog"
      :event="event" />
    <exo-confirm-dialog
      v-else
      ref="deleteConfirmDialog"
      :message="$t('agenda.message.confirmDeleteEvent')"
      :title="$t('agenda.title.confirmDeleteEvent')"
      :ok-label="$t('agenda.button.ok')"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="deleteEvent" />
    <agenda-event-reminder-drawer
      v-if="isAttendee"
      ref="reminders"
      :event="event" />
  </v-card>
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
    connectedAccount: {
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
      loading: false,
    };
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
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
    this.$root.$on('agenda-event-response-sent', () => {
      if (!this.event) {
        return;
      }
      const retrieveEventDetailsPromise = this.event.occurrence && this.event.occurrence.id ? this.$eventService.getEventOccurrence(this.event.parent.id, this.event.occurrence.id, 'all') : this.$eventService.getEventById(this.event.id, 'all');
      this.loading= true;
      retrieveEventDetailsPromise
        .then(event => this.event = event)
        .finally(() => {
          this.$root.$emit('agenda-event-response-updated');
          this.loading= false;
        });
    });
    this.$root.$on('agenda-event-reminders-saved', (event, occurrenceId, reminders) => {
      this.event.reminders = reminders;
      if (event.id && !this.event.id) {
        this.$root.$emit('agenda-refresh');
      }
      this.reset();
    });
    this.$root.$on('displayRemoteEventLoading', () => {
      this.loading= true;
    });
    this.$root.$on('hideRemoteEventLoading', () => {
      this.loading= false;
    });
  },
  methods: {
    closeDialog() {
      this.$emit('close');
    },
    deleteConfirmDialog() {
      this.$refs.deleteConfirmDialog.open();
    },
    deleteEvent() {
      this.$eventService.deleteEvent(this.event.id)
        .then(() => this.$root.$emit('agenda-event-deleted'));
    },
  }
};
</script>