<template>
  <v-card flat class="d-flex flex-column">
    <div class="d-flex flex-nowrap py-3">
      <div class="title text-truncate flex-grow-1 px-0 pt-1 mx-2">
        {{ $t('agenda.scheduleEvent') }}
      </div>
      <div class="flex-grow-0 flex-shrink-0 px-0 mr-2">
        <v-btn
          color="grey"
          icon
          @click="$emit('close')">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>
      </div>
    </div>
    <v-divider />
    <v-form
      v-if="event"
      ref="agendaEventForm"
      class="flex event-form-mobile px-3 pb-2"
      flat
      @submit="saveEvent">
      <div class="d-flex flex-column flex-grow-1">
        <label class="font-weight-bold my-2">
          {{ $t('agenda.title') }}
        </label>
        <input
          id="eventTitle"
          ref="eventTitle"
          v-model="event.summary"
          :placeholder="$t('agenda.eventTitle')"
          type="text"
          name="title"
          class="ignore-vuetify-classes"
          required
          autofocus
          @change="resetCustomValidity">
        <label class="font-weight-bold my-2">
          {{ $t('agenda') }}
        </label>
        <agenda-event-form-calendar-owner
          ref="calendarOwner"
          :event="event"
          :current-space="currentSpace" />
        <agenda-event-form-date-pickers :event="event">
          <template slot="startDateLabel">
            <label v-if="event.allDay" class="font-weight-bold my-2">{{ $t('agenda.startDate') }}</label>
            <label v-else class="font-weight-bold my-2">{{ $t('agenda.startTime') }}</label>
          </template>
          <template slot="endDateLabel">
            <label v-if="event.allDay" class="font-weight-bold my-2">{{ $t('agenda.endDate') }}</label>
            <label v-else class="font-weight-bold my-2">{{ $t('agenda.endTime') }}</label>
          </template>
        </agenda-event-form-date-pickers>
        <label class="font-weight-bold my-2">
          {{ $t('agenda.repitition') }}
        </label>
        <agenda-event-form-recurrence :event="event" />
        <agenda-event-recurrence
          v-if="event.recurrence"
          :event="event"
          class="text-wrap mt-2" />
        <label class="font-weight-bold my-2">
          {{ $t('agenda.location') }}
        </label>
        <input
          id="eventLocation"
          ref="eventLocation"
          v-model="event.location"
          :placeholder="$t('agenda.eventLocation')"
          type="text"
          name="locationEvent"
          class="ignore-vuetify-classes my-0 location-event-input">
        <label class="font-weight-bold my-2">
          {{ $t('agenda.description') }}
        </label>
        <extended-textarea
          id="eventDescription"
          ref="eventDescription"
          v-model="event.description"
          :placeholder="$t('agenda.descriptionPlaceholder')"
          :max-length="eventDescriptionTextLength"
          class="border-box-sizing py-0 my-0" />
        <label class="font-weight-bold my-2">
          {{ $t('agenda.participants') }}
        </label>
        <agenda-event-form-attendees :event="event" />
        <div class="d-flex flex-row my-2 align-center">
          <label class="font-weight-bold">{{ $t('agenda.modifyEventPermission') }}</label>
          <v-switch v-model="event.allowAttendeeToUpdate" class="pa-0 mt-0 ml-4" />
        </div>
        <div class="d-flex flex-row font-weight-regular">
          {{ $t('agenda.modifyEventPermissionDescription') }}
        </div>
      </div>
    </v-form>
    <v-divider />
    <div class="d-flex ma-2">
      <v-spacer />
      <v-btn
        class="btn ml-2 d-inline d-sm-none"
        @click="close">
        {{ $t('agenda.button.cancel') }}
      </v-btn>
      <v-btn
        :disabled="disableSaveButton"
        class="btn btn-primary ml-2"
        @click="saveEvent">
        {{ $t('agenda.button.save') }}
      </v-btn>
    </div>
  </v-card>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null,
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    saving: false,
    eventDescriptionTextLength: 1300
  }),
  computed: {
    isNew() {
      return !this.event || (!this.event.id && !this.event.parent);
    },
    eventTitle() {
      return this.event && this.event.summary;
    },
    eventTitleValid() {
      return this.eventTitle && this.eventTitle.length >= 5 && this.eventTitle.length < 1024;
    },
    eventOwner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    eventOwnerValid() {
      return this.eventOwner && (this.eventOwner.id || this.eventOwner.remoteId && this.eventOwner.providerId);
    },
    eventDescription() {
      return this.event && this.event.description || '';
    },
    eventDescriptionValid() {
      return this.eventDescription.length <= 1300;
    },
    disableSaveButton() {
      return this.saving || !this.eventTitleValid || !this.eventOwnerValid || !this.eventDescriptionValid;
    },
  },
  created() {
    this.$root.$on('agenda-event-form', () => {
      this.reset();
    });
    this.$root.$on('agenda-event-saved', this.close);
  },
  mounted() {
    this.reset();
  },
  methods:{
    close() {
      this.$emit('close');
      window.setTimeout(() => {
        this.stepper = 1;
      }, 200);
    },
    reset() {
      this.resetCustomValidity();
      window.setTimeout(() => {
        if (this.$refs.eventTitle) {
          this.$refs.eventTitle.focus();
        }
      }, 200);
    },
    resetCustomValidity() {
      if (this.$refs.eventTitle) {
        this.$refs.eventTitle.setCustomValidity('');
      }
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
      }
    },
    validateForm() {
      this.resetCustomValidity();
      this.$refs.calendarOwner.validateForm();

      if (!this.event.summary) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingEventTitle'));
      } else if (this.event.summary.trim().length < 5 || this.event.summary.trim().length > 1024) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingLengthEventTitle'));
      }

      if (!this.$refs.agendaEventForm.validate() // Vuetify rules
          || !this.$refs.agendaEventForm.$el.reportValidity()) { // Standard HTML rules
        return;
      }

      return true;
    },
    saveEvent() {
      if (!this.validateForm()) {
        return;
      }
      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      this.$root.$emit('agenda-event-save', this.event);
    },
  }
};
</script>