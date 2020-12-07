<template>
  <div v-if="conferenceProvider" class="d-flex flex-row">
    <i class="uiIconVideo darkGreyIcon uiIcon32x32 my-3 mr-11"></i>
    <div class="d-flex flex-column">
      <template v-if="eventConference">
        <div
          v-if="eventConferenceId"
          v-autolinker="eventConferenceUrl"></div>
        <v-alert
          v-else
          type="info">
          {{ $t('agenda.webConferenceScheduled') }}
        </v-alert>
        <v-btn
          :loading="loading"
          :title="$t('agenda.deleteEventConference')"
          color="grey"
          icon
          dark
          class="ml-auto"
          @click="deleteCallURL">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>
      </template>
      <v-btn
        v-else-if="eventConferenceId"
        :loading="loading"
        class="btn btn-primary border-radius"
        @click="createCallUrl">
        {{ $t('agenda.createEventConference') }}
      </v-btn>
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
    currentSpace: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    loading: false,
    conferenceProviders: null,
  }),
  computed:{
    enabledConferenceProviderName() {
      return this.settings
          && this.settings.webConferenceProviders
          && this.settings.webConferenceProviders.length
          && this.settings.webConferenceProviders[0];
    },
    conferenceProvider() {
      return this.conferenceProviders && this.conferenceProviders.find(provider => provider.getType() === this.enabledConferenceProviderName);
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
      return this.eventConference && this.eventConference.uri;
    },
  },
  mounted() {
    if (eXo && eXo.webConferencing) {
      eXo.webConferencing.getAllProviders().then(providers => {
        this.conferenceProviders = providers && providers.find(provider => provider.isInitialized);
      });
    }
  },
  methods:{
    createCallUrl() {
      const callId = parseInt(Math.random() * 100000000);
      this.event.conferences = [{
        type: this.enabledConferenceProviderName,
        uri: callId,
      }];
    },
    deleteCallURL() {
      this.event.conferences = null;
    },
  }
};
</script>
