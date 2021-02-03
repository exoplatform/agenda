<template>
  <v-list>
    <!-- <agenda-pending-invitation-item /> -->
    <div v-for="i in pendingInvitations" :key="i">
      {{ i }}
    </div>
    <v-flex v-if="hasMore" class="agendaLoadMoreParent d-flex my-4 border-box-sizing">
      <v-btn
        :loading="loading"
        :disabled="loading"
        class="btn mx-auto"
        @click="loadMore">
        {{ $t('agenda.button.loadMore') }}
      </v-btn>
    </v-flex>
  </v-list>
</template>

<script>
export default {
  props :{
    pendingInvitations: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    loading: false,
    limit: 20,
    pageSize: 20,
    totalSize: 0,
    events:[],
  }),
  computed:{
    hasMore() {
      return this.limit < this.totalSize;
    },
  },
  watch:{
    loading() {
      if (this.loading) {
        this.$emit('start-loading');
      } else {
        this.$emit('end-loading');
      }
    },
  },
  methods: {
    loadMore() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
  }
};
</script>