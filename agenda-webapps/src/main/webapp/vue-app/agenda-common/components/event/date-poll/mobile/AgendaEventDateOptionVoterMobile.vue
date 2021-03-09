<template>
  <exo-user-avatars-list
    :users="voters"
    :max="2"
    class="d-inline-block" />
</template>

<script>
export default {
  props: {
    voters: {
      type: Array,
      default: () => null,
    }
  }
};
</script>
