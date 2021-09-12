import visa
import numpy as np
import matplotlib.pyplot as plt


title = "Diramics_2F200_T50_8mA"
measurement = "TEFF"

address = "GPIB0::8::INSTR"
rm = visa.ResourceManager()
NFA = rm.open_resource(address)
NFA.timeout = None

frequency_list = np.linspace(float(NFA.query("SENS:FREQ:STAR?")),float(NFA.query("SENS:FREQ:STOP?"))
                             ,int(NFA.query("SENS:SWE:POIN?")))
np.savetxt(title + ".csv"
           ,np.c_[frequency_list,np.fromstring(NFA.query("FETC:CORR:"+measurement+"?"),sep=",")]
           ,delimiter=",")

NFA.close()
