<template>
  <div class="connector-status">
    <div v-if="connectedAccountName && isConnectedConnectorEnabled" class="connector-connected d-flex">
      <v-avatar tile size="24">
        <img
          :alt="connectedAccount.name"
          :src="connectedAccountIconSource">
      </v-avatar>
      <a
        class="mx-2 my-auto"
        @click="openPersonalCalendarDrawer">
        {{ connectedAccountName }}
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
    connectedAccount: {
      type: Array,
      default: () => []
    },
    isConnectedConnectorEnabled: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
  },
  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    },
  }
};
</script>