<template>
  <div class="connector-status">
    <div v-if="connectedConnectorUser" class="connector-connected d-flex">
      <agenda-connector-avatar
        :connector="connectedConnector"
        size="24" />
      <a
        class="mx-2 my-auto"
        @click="openPersonalCalendarDrawer">
        {{ connectedConnectorUser }}
      </a>
    </div>
    <div
      v-else
      class="connect-button"
      @click="openPersonalCalendarDrawer">
      <slot
        name="connectButton">
      </slot>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => null
    },
  },
  computed: {
    connectedConnector() {
      return this.connectors.find(connector => connector.user);
    },
    connectedConnectorName() {
      return this.connectedConnector && this.connectedConnector.name || '';
    },
    connectedConnectorUser() {
      return this.connectedConnector && this.connectedConnector.user || '';
    },
  },
  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
  }
};
</script>