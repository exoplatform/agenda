<template>
  <div
    v-if="connectors.length > 0 && !connectedConnector"
    class="d-flex align-center">
    <v-btn
      :title="$t('agenda.connectYourPersonalAgenda')"
      icon
      :max-width="width"
      :max-height="height" 
      @click="openPersonalCalendarDrawer">
      <v-icon :size="size" class="text-light-color">
        fas fa-plug
      </v-icon>
    </v-btn>
  </div>
  <div
    v-else
    class="d-flex align-center">
    <v-btn
      :title="showREvents ? $t('agenda.hideRemoteEvents') : $t('agenda.showRemoteEvents')"
      icon
      :max-width="width"
      :max-height="height"
      @click="showRemoteEvents">
      <v-icon :size="size" :color="showREvents ? 'primary' : 'text-light-color'">
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
  },

  computed: {
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    showREvents() {
      return this.settings && this.settings.showRemoteEventsForAgenda;
    },
  },

  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    showRemoteEvents() {
      this.$root.$emit('agenda-show-remote-change',!this.showREvents);
    },
  },
};
</script>