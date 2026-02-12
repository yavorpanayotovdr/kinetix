from kinetix_risk import __version__


def test_version():
    assert __version__ == "0.1.0"


def test_package_importable():
    import kinetix_risk

    assert kinetix_risk is not None
