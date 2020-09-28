<template>
  <v-card
    v-if="event"
    flat
    class="event-details d-flex flex-column">
    <v-toolbar
      flat
      class="event-details-header border-box-sizing flex-grow-0">
      <v-flex class="d-flex align-center">
        <div class="event-title title text-truncate">
          {{ event.summary }}
        </div>
        <div class="pl-3 pt-1">
          {{ $t('agenda.label.in') }}
        </div>
        <div class="d-flex flex-row pl-3">
          <v-avatar
            height="32"
            min-height="32"
            width="32"
            min-width="32"
            size="32"
            class="mx-3 spaceAvatar space-avatar-header">
            <v-img :src="ownerAvatarUrl" />
          </v-avatar>
          <div class="pt-2">
            <a href="#" class="text-truncate">{{ ownerDisplayName }}</a>
          </div>
        </div>
      </v-flex>
      <v-spacer />
      <v-toolbar-items>
        <v-menu
          bottom
          left
          offset-y>
          <template v-slot:activator="{ on, attrs }">
            <v-btn
              icon
              v-bind="attrs"
              v-on="on">
              <v-icon>mdi-dots-vertical</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item v-if="canEdit" @click="$root.$emit('agenda-event-form', event)">
              <v-list-item-title>
                {{ $t('agenda.details.header.menu.edit') }}
              </v-list-item-title>
            </v-list-item>
            <v-list-item v-if="canEdit" @click="deleteConfirmDialog">
              <v-list-item-title>
                {{ $t('agenda.details.header.menu.delete') }}
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn
          class="my-auto mr-2"
          color="grey"
          icon
          @click="closeDialog">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>
      </v-toolbar-items>
    </v-toolbar>
    <v-divider class="flex-grow-0" />
    <v-container class="event-details-body flex-grow-1">
      <v-row class="pa-5">
        <v-col>
          <v-row class="event-date align-center d-flex pb-5">
            <i class="uiIconDatePicker darkGreyIcon uiIcon32x32 pr-5"></i>
            <div class="d-inline-flex">
              <date-format
                :value="event.start"
                :format="fullDateFormat"
                class="mr-1" />
              <template v-if="!sameDayDates">
                -
                <date-format
                  :value="event.end"
                  :format="fullDateFormat"
                  class="ml-1" />
              </template>
            </div>
          </v-row>
          <v-row class="event-time align-center d-flex pb-5">
            <i class="uiIconClock darkGreyIcon uiIcon32x32 pr-5"></i>
            <div class="d-inline-flex">
              <template v-if="event.allDay">
                {{ $t('agenda.allDay') }}
              </template>
              <template v-else>
                <date-format
                  :value="event.start"
                  :format="dateTimeFormat"
                  class="mr-1" />
                -
                <date-format
                  :value="event.end"
                  :format="dateTimeFormat"
                  class="ml-1" />
              </template>
            </div>
          </v-row>
          <v-row v-if="hasRecurrence" class="event-recurrence align-center d-flex pl-1 pb-5">
            <i class="uiIconRefresh darkGreyIcon uiIcon32x32 pr-5"></i>
            <agenda-event-recurrence :event="event" />
          </v-row>
          <v-row v-if="event.location" class="event-location align-center d-flex pb-5">
            <i class="uiIconCheckin darkGreyIcon uiIcon32x32 pr-5"></i>
            <span>{{ event.location }}</span>
          </v-row>
          <v-row v-if="event.description" class="event-description d-flex pb-5">
            <i class="uiIconDescription darkGreyIcon uiIcon32x32 pr-5"></i>
            <span class="mt-1">{{ event.description }}</span>
          </v-row>
          <v-row
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
          </v-row>
        </v-col>
        <v-col class="flex-grow-0">
          <v-divider vertical />
        </v-col>
        <agenda-event-attendees
          ref="agendaAttendees"
          :event="event"
          class="ml-10" />
      </v-row>
    </v-container>
    <v-divider />
    <div class="d-flex">
      <div class="flex-grow-1"></div>
      <agenda-event-attendee-buttons
        ref="eventAttendeeButtons"
        :event="event"
        class="flex-grow-0 my-6 mr-10" />
    </div>
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
  </v-card>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
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
      }
    };
  },
  computed: {
    hasRecurrence() {
      return this.event.recurrence || this.event.parent && this.event.parent.recurrence;
    },
    canEdit() {
      return this.event.acl && this.event.acl.canEdit;
    },
    ownerProfile() {
      const owner = this.event && this.event.calendar && this.event.calendar.owner;
      return owner && (owner.profile || owner.space);
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
    this.$root.$on('agenda-event-details-opened', this.reset);
    this.$root.$on('agenda-event-response-sent', event => {
      this.event.attendees = event.attendees;
      if (event.id && !this.event.id) {
        this.$root.$emit('refresh');
      }
      this.reset();
    });
  },
  methods: {
    reset() {
      if (this.$refs.eventAttendeeButtons) {
        this.$refs.eventAttendeeButtons.reset();
      }
    },
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