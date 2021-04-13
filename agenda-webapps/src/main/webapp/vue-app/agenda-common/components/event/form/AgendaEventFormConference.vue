<template>
  <div class="d-flex flex-row">
    <div v-if="isConferenceEnabled" class="d-flex flex-row my-auto">
      <template v-if="eventConference">
        <div
          v-if="eventConferenceUrl"
          v-autolinker="eventConferenceUrl"
          class="my-auto"></div>
        <v-chip
          v-else
          color="primary"
          class="my-auto"
          outlined>
          <span class="primary--text">
            {{ $t('agenda.webConferenceScheduled') }}
          </span>
        </v-chip>
        <v-btn
          :loading="loading"
          :title="$t('agenda.deleteEventConference')"
          color="grey"
          icon
          dark
          class="ms-auto"
          @click="deleteCallURL">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>
      </template>
      <v-btn
        v-else
        :loading="loading"
        class="btn btn-primary border-radius my-auto"
        @click="createCallUrl">
        {{ $t('agenda.createEventConference') }}
      </v-btn>
    </div>
    <div v-else class="d-flex flex-row my-auto">
      <input
        id="eventCallURL"
        ref="eventCallURL"
        v-model="conferenceURL"
        :placeholder="$t('agenda.webConferenceURL')"
        type="text"
        name="locationEvent"
        class="ignore-vuetify-classes my-3 location-event-input">
    </div>
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    settings: {
      type: Object,
      default: () => null,
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    loading: false,
    conferenceURL: null,
  }),
  computed: {
    isConferenceEnabled() {
      return this.conferenceProvider && (!this.eventConferenceType || this.conferenceProvider.getType() === this.eventConferenceType);
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
    eventConferenceId() {
      return this.eventConference && this.eventConference.id;
    },
    eventConferenceType() {
      return this.eventConference && this.eventConference.type;
    },
    eventConferenceUrl() {
      return this.eventConference && this.eventConference.url;
    },
  },
  watch: {
    conferenceURL(newVal) {
      if (!newVal) {
        this.event.conferences = [];
      } else {
        this.event.conferences = [{
          url: newVal,
          type: 'manual',
        }];
      }
    },
  },
  mounted() {
    if (this.event && this.event.conferences && this.event.conferences.length) {
      this.conferenceURL = this.event.conferences[0].url;
    }
  },
  methods: {
    createCallUrl() {
      this.$set(this.event, 'conferences', [{
        type: this.conferenceProvider.getType(),
      }]);
    },
    deleteCallURL() {
      this.$set(this.event, 'conferences', null);
    },
  }
};
</script>
