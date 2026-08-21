"""Preserve canonical deployment failure state until terminal lock cleanup."""

from __future__ import annotations

from ansible.plugins.strategy.linear import StrategyModule as LinearStrategyModule


_transaction_failed = False
_transaction_active = False
_failed_transaction_hosts: set[str] = set()
_unreachable_transaction_hosts: set[str] = set()


class StrategyModule(LinearStrategyModule):
    """Stop later mutations, permit lock cleanup, and retain the failed result."""

    def run(self, iterator, play_context):
        global _transaction_active, _transaction_failed

        cleanup_play = bool(
            iterator._play.vars.get("canonical_lock_cleanup_play", False)
        )
        if _transaction_failed and not cleanup_play:
            return int(self._tqm.RUN_OK)

        play_result = super().run(iterator, play_context)
        play_hosts = self._inventory.get_hosts(
            iterator._play.hosts,
            ignore_restrictions=True,
        )
        if any(
            self._variable_manager.get_vars(
                play=iterator._play,
                host=host,
            ).get("deployment_lock_acquired", False)
            for host in play_hosts
        ):
            _transaction_active = True

        if cleanup_play:
            terminal_result = play_result
            if _transaction_failed:
                for host_name in _failed_transaction_hosts:
                    self._tqm._failed_hosts[host_name] = True
                for host_name in _unreachable_transaction_hosts:
                    self._tqm._unreachable_hosts[host_name] = True

                if _failed_transaction_hosts:
                    terminal_result |= self._tqm.RUN_FAILED_HOSTS
                if _unreachable_transaction_hosts:
                    terminal_result |= self._tqm.RUN_UNREACHABLE_HOSTS
            return terminal_result

        failed_host_names = tuple(iterator.get_failed_hosts())
        unreachable_host_names = tuple(self._tqm._unreachable_hosts)
        lock_owning_failures = (
            list(failed_host_names) if _transaction_active else []
        )
        lock_owning_unreachable_hosts = (
            list(unreachable_host_names) if _transaction_active else []
        )

        if lock_owning_failures or lock_owning_unreachable_hosts:
            _transaction_failed = True
            _failed_transaction_hosts.update(lock_owning_failures)
            _unreachable_transaction_hosts.update(lock_owning_unreachable_hosts)
            for host_name in lock_owning_failures:
                host = self._inventory.get_host(host_name)
                self._tqm._failed_hosts.pop(host_name, None)
                iterator.clear_host_errors(host)
                while host_name in iterator._play._removed_hosts:
                    iterator._play._removed_hosts.remove(host_name)
            for host_name in lock_owning_unreachable_hosts:
                self._tqm._unreachable_hosts.pop(host_name, None)
                while host_name in iterator._play._removed_hosts:
                    iterator._play._removed_hosts.remove(host_name)
            return int(self._tqm.RUN_OK)

        return play_result
