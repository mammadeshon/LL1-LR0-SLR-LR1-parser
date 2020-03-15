# LL(1), LR(0), SLR, LR(1) parser
just does everything for you ....  
Reads grammar from file in BNF format, reads tokens from file, generates state machines and corresponding parse tables,
reports if there was any conflicts in table creation process( conflicts like first/follow, follow/follow, first/first in LL(1) parser
and conflicts like shift/reduce in other parsers ), writes state machines and tables in file separately,
and finally parses the given tokens and prints stack changes in every step.  
