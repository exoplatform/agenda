<template>
  <exo-drawer
    ref="quickAddEventDrawer"
    right
    body-classes="hide-scroll"
    class="quickAddEventDrawer"
    @closed="cancelEventCreation">
    <template slot="title">
      {{ $t('agenda.title.addEvent') }}
    </template>
    <template slot="content">
      <v-form
        v-if="event"
        ref="agendaEventForm"
        class="flex event-form quick-add"
        flat
        @submit="createEvent">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-row">
            <input
              id="eventTitle"
              ref="eventTitle"
              v-model="event.summary"
              :placeholder="$t('agenda.eventTitle')"
              type="text"
              name="title"
              class="ignore-vuetify-classes my-3"
              required
              autofocus
              @change="resetCustomValidity">
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0">
              <i class="uiIconPLFEventTask text-color uiIcon32x32 mt-4 mx-3"></i>
            </v-flex>
            <agenda-event-form-calendar-owner
              ref="calendarOwner"
              :event="event"
              :current-space="currentSpace"
              class="ml-1 pr-1" />
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0 pt-4 my-2 mx-3">
              <i class="uiIconClock text-color uiIcon32x32"></i>
            </v-flex>
            <agenda-event-form-date-pickers :event="event" />
          </div>
          <div class="d-flex flex-row">
            <i class="uiIconLocation text-color uiIcon32x32 mt-4 mx-3"></i>
            <input
              id="eventLocation"
              ref="eventLocation"
              v-model="event.location"
              :placeholder="$t('agenda.eventLocation')"
              type="text"
              name="locationEvent"
              class="ignore-vuetify-classes my-3 location-event-input">
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0">
              <i class="uiIconDescription text-color uiIcon32x32 my-3 mx-3"></i>
            </v-flex>
            <textarea
              id="eventDescription"
              ref="eventDescription"
              v-model="event.description"
              :placeholder="$t('agenda.description')"
              type="text"
              name="description"
              rows="20"
              maxlength="2000"
              class="ignore-vuetify-classes my-3 description-event-textarea textarea-no-resize">
            </textarea>
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0">
              <i class="uiIconGroup text-color uiIcon32x32 mt-4 mx-3"></i>
            </v-flex>
            <agenda-event-form-attendees :event="event" class="pr-1" />
          </div>
        </div>
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-btn
          class="btn ml-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-spacer />
        <v-btn
          class="btn ml-2"
          @click="openCompleteEventForm">
          {{ $t('agenda.button.moreDetails') }}
        </v-btn>
        <v-btn
          :disabled="disableSaveButton"
          class="btn btn-primary ml-2"
          @click="createEvent">
          {{ $t('agenda.label.create') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    event: null,
    saving: false,
  }),
  computed: {
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
    disableSaveButton() {
      return this.saving || !this.eventTitleValid || !this.eventOwnerValid;
    },
  },
  created() {
    this.$root.$on('agenda-event-quick-form-open', event => {
      this.event = null;
      this.$nextTick().then(() => {
        this.event = event;
        this.open();
        this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', this.event));
      });
    });
  },
  methods:{
    close() {
      this.$refs.quickAddEventDrawer.close();
    },
    open() {
      this.resetCustomValidity();
      this.$refs.quickAddEventDrawer.open();
      window.setTimeout(() => {
        if (this.$refs.eventTitle) {
          this.$refs.eventTitle.focus();
        }
      }, 200);
    },
    openCompleteEventForm() {
      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      delete this.event.startDate;
      delete this.event.endDate;

      this.$refs.quickAddEventDrawer.close();
      this.$root.$emit('agenda-event-form', this.event);
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
    cancelEventCreation() {
      this.event = null;
      this.$root.$emit('agenda-event-quick-form-cancel');
    },
    createEvent() {
      if (!this.validateForm()) {
        return;
      }
      this.saving = true;

      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      delete this.event.startDate;
      delete this.event.endDate;

      this.$eventService.createEvent(this.event)
        .then(() => {
          this.$root.$emit('refresh');
          this.close();
        })
        .finally(() => this.saving = false);
    },
  }
};
</script>