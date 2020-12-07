<template>
  <div v-if="conferenceProvider" class="d-flex flex-row">
    <i class="uiIconVideo darkGreyIcon uiIcon32x32 my-3 mr-11"></i>
    <div class="d-flex flex-column">
      <template v-if="eventConferenceUrl">
        <div v-autolinker="eventConferenceUrl"></div>
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
        v-else
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
    conferenceProvider: null,
  }),
  computed:{
    eventConferences() {
      return this.event && this.event.conferences;
    },
    eventConference() {
      // For now, use only one web conference per event
      return this.eventConferences
          && this.eventConferences.length
          && this.eventConferences[0];
    },
    eventConferenceUrl() {
      return this.eventConference && this.eventConference.uri;
    },
  },
  mounted() {
    if (eXo && eXo.webConferencing) {
      eXo.webConferencing.getAllProviders().then(providers => {
        if (providers && providers.length) {
          if (this.eventConference) {
            this.conferenceProvider = providers.find(provider => provider.isInitialized && provider.getType && provider.getType() === this.eventConference.type);
          } else {
            this.conferenceProvider = providers.find(provider => provider.isInitialized && provider.getUrl);
          }
        }
      });
    }
  },
  methods:{
    createCallUrl() {
      if (!this.eventConference) {
        this.event.conferences = [{
          type: this.conferenceProvider.getType(),
        }];
      }
      //       this.eventConference.uri;
    },
    deleteCallURL() {
      this.event.conferences = null;
    }
  }
};
</script>
