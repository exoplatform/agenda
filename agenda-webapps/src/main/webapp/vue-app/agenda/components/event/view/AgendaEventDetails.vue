<template>
  <v-card flat class="event-details d-flex flex-column">
    <v-toolbar
      flat
      class="event-details-header border-box-sizing flex-grow-0">
      <v-toolbar-title class="d-flex align-center">
        <span class="event-title px-2 text-truncate">
          {{ event.summary }}
        </span>
        <span class="px-4">
          {{ $t('agenda.label.in') }}
        </span>
        <template v-if="ownerProfile">
          <v-avatar
            height="24"
            min-height="24"
            width="24"
            min-width="24"
            size="24"
            class="mx-3 spaceAvatar">
            <v-img :src="ownerAvatarUrl" />
          </v-avatar>
          <a href="#" class="text-truncate">{{ ownerDisplayName }}</a>
        </template>
      </v-toolbar-title>
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
            <v-list-item>
              <v-list-item-title>
                {{ $t('agenda.details.header.menu.export') }}
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
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
          <v-row v-if="event.location" class="event-location align-center d-flex pb-5">
            <i class="uiIconLocation darkGreyIcon uiIcon32x32 pr-5"></i>
            <span>{{ event.location }}</span>
          </v-row>
          <v-row v-if="event.description" class="event-description d-flex pb-5">
            <i class="uiIconDescription darkGreyIcon uiIcon32x32 pr-5"></i>
            <span>{{ event.description }}</span>
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
          <v-row class="event-external-agendas align-center d-flex pb-5">
            <div class="alert alert-info mt-5 rounded-lg">
              <i class="uiIconInformation secondary--text"></i>
              {{ $t('agenda.details.calendar.synchronize') }}
            </div>
          </v-row>
          <v-row class="event-external-agendas align-center d-flex pb-5">
            <i class="uiIconConnect darkGreyIcon uiIcon32x32 pr-5"></i>
            <div class="external-agendas-selector align-center">
              <span>{{ $t('agenda.details.calendar.connectTo') }}</span>
              <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
                <option>{{ $t('agenda.details.calendar.google') }}</option>
                <option>{{ $t('agenda.details.calendar.office') }}</option>
                <option>{{ $t('agenda.details.calendar.ics') }}</option>
              </select>
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
    <div class="d-flex flex-row flex-grow-0 ml-auto mx-md-10 my-2 mb-md-10">
      <v-btn class="btn ml-auto" @click="closeDialog">
        {{ $t('agenda.button.close') }}
      </v-btn>
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
        hour12: false
      }
    };
  },
  computed: {
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
      if (this.$refs.agendaAttendees) {
        this.$refs.agendaAttendees.reset();
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