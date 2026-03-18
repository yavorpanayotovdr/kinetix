import signal
from unittest.mock import MagicMock, patch

import pytest

pytestmark = pytest.mark.unit


class TestSigtermHandler:
    def test_sigterm_handler_is_registered_during_serve(self):
        """Verify that serve() registers a SIGTERM handler."""
        with patch("kinetix_risk.server.grpc") as mock_grpc, \
             patch("kinetix_risk.server.prometheus_client"), \
             patch("kinetix_risk.server.signal.signal") as mock_signal, \
             patch("kinetix_risk.server.ModelStore"):

            mock_server = MagicMock()
            mock_grpc.server.return_value = mock_server
            mock_server.add_insecure_port.return_value = 50051
            # Make wait_for_termination return immediately
            mock_server.wait_for_termination.return_value = None

            from kinetix_risk.server import serve
            serve()

            # Verify SIGTERM handler was registered
            sigterm_calls = [
                call for call in mock_signal.call_args_list
                if call[0][0] == signal.SIGTERM
            ]
            assert len(sigterm_calls) == 1, "SIGTERM handler should be registered"

    def test_sigterm_handler_calls_server_stop_with_grace_period(self):
        """Verify the SIGTERM handler calls server.stop(grace=30)."""
        with patch("kinetix_risk.server.grpc") as mock_grpc, \
             patch("kinetix_risk.server.prometheus_client"), \
             patch("kinetix_risk.server.signal.signal") as mock_signal, \
             patch("kinetix_risk.server.ModelStore"):

            mock_server = MagicMock()
            mock_grpc.server.return_value = mock_server
            mock_server.add_insecure_port.return_value = 50051
            mock_server.wait_for_termination.return_value = None
            mock_stopped = MagicMock()
            mock_server.stop.return_value = mock_stopped

            from kinetix_risk.server import serve
            serve()

            # Extract the registered handler
            sigterm_calls = [
                call for call in mock_signal.call_args_list
                if call[0][0] == signal.SIGTERM
            ]
            handler = sigterm_calls[0][0][1]

            # Invoke the handler
            handler(signal.SIGTERM, None)

            mock_server.stop.assert_called_once_with(grace=30)
            mock_stopped.wait.assert_called_once()
