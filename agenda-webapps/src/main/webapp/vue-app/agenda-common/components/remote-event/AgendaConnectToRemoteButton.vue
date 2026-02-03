<template>
  <div v-if="connectorsLoaded" class="d-flex align-center">
    <v-btn
      v-if="!connectedConnector"
      :title="$t('agenda.connectYourPersonalAgenda')"
      icon
      :max-width="width"
      :max-height="height" 
      @click="openPersonalCalendarDrawer">
      <v-icon :size="size" class="text-light-color">
        fas fa-plug
      </v-icon>
    </v-btn>
    <v-btn
      v-else
      :title="showDefaultRemoteEvents ? $t('agenda.hideRemoteEvents') : $t('agenda.showRemoteEvents')"
      icon
      :max-width="width"
      :max-height="height"
      @click="showRemoteEvents">
      <v-icon :size="size" :color="showDefaultRemoteEvents ? 'primary' : 'text-light-color'">
        fas fa-calendar-check
      </v-icon>
    </v-btn>
  </div>
</template>

<script>
export default {
  props: {
    size: {
      type: String,
      default: '18'
    },
    height: {
      type: String,
      default: '28'
    },
    width: {
      type: String,
      default: '28'
    },
    connectors: {
      type: Array,
      default: () => null,
    },
    settings: {
      type: Object,
      default: () => null,
    },
    showDefaultRemoteEvents: {
      type: Boolean,
      default: false,
    },
  },

  data() {
    return {
      connectorsLoaded: false,
    };
  },

  created() {
    this.$root.$on('connectors-loaded', this.connectorsLoaded = true);
  },

  computed: {
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
  },

  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    showRemoteEvents() {
      this.$root.$emit('agenda-show-remote-change',!this.showDefaultRemoteEvents);
    },
  },
};
</script>